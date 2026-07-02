package com.psocks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ProxyVpnService extends VpnService {
	private static final String TAG = "ProxyVpnService";
	private static final String CHANNEL_ID = "psocks_vpn";
	public static final String ACTION_CONNECT = "com.psocks.action.CONNECT";
	public static final String ACTION_DISCONNECT = "com.psocks.action.DISCONNECT";

	private static native void TProxyStartService(String configPath, int fd);
	private static native void TProxyStopService();
	private static native long[] TProxyGetStats();

	private static final boolean NATIVE_READY;

	static {
		boolean loaded;
		try {
			System.loadLibrary("psocks-engine");
			loaded = true;
		} catch (UnsatisfiedLinkError e) {
			loaded = false;
			Log.e(TAG, "Native SOCKS engine is missing from the APK.", e);
		}
		NATIVE_READY = loaded;
	}

	// Config passed via Intent from the main process.
	// This avoids cross-process SharedPreferences issues.
	private static final class Config {
		String proxyHost = "127.0.0.1";
		String proxyUdpHost = "";
		int proxyPort = 1080;
		String proxyUser = "";
		String proxyPassword = "";
		String dns4 = "8.8.8.8";
		String dns6 = "2001:4860:4860::8888";
		boolean ipv4 = true;
		boolean ipv6 = true;
		boolean udpOverTcp = false;
		boolean remoteDns = true;
		int routeMode = SettingsStore.ROUTE_ALL_APPS;
		List<String> selectedApps = new ArrayList<>();

		static Config fromIntent(Intent intent) {
			Config c = new Config();
			if (intent == null) {
				// fallback: read from SharedPreferences (boot receiver path)
				return c;
			}
			c.proxyHost = intent.getStringExtra(SettingsStore.EXTRA_PROXY_HOST);
			if (c.proxyHost == null) return c;
			c.proxyUdpHost = intent.getStringExtra(SettingsStore.EXTRA_PROXY_UDP_HOST);
			if (c.proxyUdpHost == null) c.proxyUdpHost = "";
			c.proxyPort = intent.getIntExtra(SettingsStore.EXTRA_PROXY_PORT, 1080);
			c.proxyUser = intent.getStringExtra(SettingsStore.EXTRA_PROXY_USER);
			if (c.proxyUser == null) c.proxyUser = "";
			c.proxyPassword = intent.getStringExtra(SettingsStore.EXTRA_PROXY_PASS);
			if (c.proxyPassword == null) c.proxyPassword = "";
			c.dns4 = intent.getStringExtra(SettingsStore.EXTRA_DNS4);
			if (c.dns4 == null) c.dns4 = "8.8.8.8";
			c.dns6 = intent.getStringExtra(SettingsStore.EXTRA_DNS6);
			if (c.dns6 == null) c.dns6 = "2001:4860:4860::8888";
			c.ipv4 = intent.getBooleanExtra(SettingsStore.EXTRA_IPV4, true);
			c.ipv6 = intent.getBooleanExtra(SettingsStore.EXTRA_IPV6, true);
			c.udpOverTcp = intent.getBooleanExtra(SettingsStore.EXTRA_UDP_OVER_TCP, false);
			c.remoteDns = intent.getBooleanExtra(SettingsStore.EXTRA_REMOTE_DNS, true);
			c.routeMode = intent.getIntExtra(SettingsStore.EXTRA_ROUTE_MODE,
				SettingsStore.ROUTE_ALL_APPS);
			ArrayList<String> apps = intent.getStringArrayListExtra(
				SettingsStore.EXTRA_SELECTED_APPS);
			if (apps != null) {
				c.selectedApps = apps;
			}
			return c;
		}
	}

	private ParcelFileDescriptor tun;
	private Config currentConfig;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
			stopVpn();
			return START_NOT_STICKY;
		}
		startVpn(intent);
		return START_STICKY;
	}

	@Override
	public void onRevoke() {
		stopVpn();
		super.onRevoke();
	}

	private void startVpn(Intent intent) {
		if (tun != null) {
			closeCurrentVpn(false);
		}
		if (!NATIVE_READY) {
			stopSelf();
			return;
		}

		Config cfg = Config.fromIntent(intent);
		currentConfig = cfg;

		VpnService.Builder builder = new VpnService.Builder()
				.setBlocking(false)
				.setMtu(8500);
		String session = configureNetwork(builder, cfg);
		if (!configureApplications(builder, cfg)) {
			stopSelf();
			return;
		}
		builder.setSession(session);

		tun = builder.establish();
		if (tun == null) {
			stopSelf();
			return;
		}

		File config = new File(getCacheDir(), "psocks.yml");
		try {
			writeEngineConfig(config, cfg);
		} catch (IOException e) {
			stopVpn();
			return;
		}

		createNotificationChannel();
		startForegroundCompat();
		TProxyStartService(config.getAbsolutePath(), tun.getFd());
	}

	private String configureNetwork(VpnService.Builder builder, Config cfg) {
		StringBuilder session = new StringBuilder("PSocks ");
		if (cfg.ipv4) {
			builder.addAddress("198.18.0.1", 32);
			builder.addRoute("0.0.0.0", 0);
			if (!cfg.remoteDns && !cfg.dns4.isEmpty()) {
				builder.addDnsServer(cfg.dns4);
			}
			session.append("IPv4");
		}
		if (cfg.ipv6) {
			builder.addAddress("fc00::1", 128);
			builder.addRoute("::", 0);
			if (!cfg.remoteDns && !cfg.dns6.isEmpty()) {
				builder.addDnsServer(cfg.dns6);
			}
			if (cfg.ipv4) {
				session.append("+");
			}
			session.append("IPv6");
		}
		if (cfg.remoteDns) {
			builder.addDnsServer("198.18.0.2");
		}
		return session.toString();
	}

	private boolean configureApplications(VpnService.Builder builder, Config cfg) {
		int mode = cfg.routeMode;
		if (mode == SettingsStore.ROUTE_SELECTED_APPS) {
			List<String> selectedApps = cfg.selectedApps;
			if (selectedApps.isEmpty()) {
				Log.w(TAG, "Selected-app mode has no package names.");
				return false;
			}
			int allowedCount = 0;
			for (String packageName : selectedApps) {
				try {
					builder.addAllowedApplication(packageName);
					allowedCount++;
					Log.i(TAG, "VPN allowed app: " + packageName);
				} catch (NameNotFoundException ignored) {
					Log.w(TAG, "Selected app is no longer installed: " + packageName);
				}
			}
			return allowedCount > 0;
		}

		try {
			builder.addDisallowedApplication(getPackageName());
		} catch (NameNotFoundException ignored) {
		}
		if (mode == SettingsStore.ROUTE_EXCLUDED_APPS) {
			for (String packageName : cfg.selectedApps) {
				try {
					builder.addDisallowedApplication(packageName);
				} catch (NameNotFoundException ignored) {
				}
			}
		}
		return true;
	}

	private void writeEngineConfig(File file, Config cfg) throws IOException {
		try (FileOutputStream out = new FileOutputStream(file, false)) {
			StringBuilder config = new StringBuilder();
			config.append("misc:\n")
					.append("  task-stack-size: ").append(81920).append('\n')
					.append("tunnel:\n")
					.append("  mtu: ").append(8500).append('\n')
					.append("socks5:\n")
					.append("  port: ").append(cfg.proxyPort).append('\n')
					.append("  address: '").append(escapeYaml(cfg.proxyHost)).append("'\n")
					.append("  udp: '").append(cfg.udpOverTcp ? "tcp" : "udp").append("'\n");
			if (!cfg.proxyUdpHost.isEmpty()) {
				config.append("  udp-address: '").append(escapeYaml(cfg.proxyUdpHost)).append("'\n");
			}
			if (!cfg.proxyUser.isEmpty() && !cfg.proxyPassword.isEmpty()) {
				config.append("  username: '").append(escapeYaml(cfg.proxyUser)).append("'\n")
						.append("  password: '").append(escapeYaml(cfg.proxyPassword)).append("'\n");
			}
			if (cfg.remoteDns) {
				config.append("mapdns:\n")
						.append("  address: 198.18.0.2\n")
						.append("  port: 53\n")
						.append("  network: 240.0.0.0\n")
						.append("  netmask: 240.0.0.0\n")
						.append("  cache-size: 10000\n");
			}
			out.write(config.toString().getBytes());
		}
	}

	private String escapeYaml(String value) {
		return value.replace("'", "''");
	}

	private void stopVpn() {
		closeCurrentVpn(true);
	}

	private void closeCurrentVpn(boolean stopService) {
		stopForeground(true);
		if (NATIVE_READY) {
			TProxyStopService();
		}
		if (tun != null) {
			try {
				tun.close();
			} catch (IOException ignored) {
			}
			tun = null;
		}
		if (stopService) {
			currentConfig = null;
			stopSelf();
		}
	}

	private void startForegroundCompat() {
		Intent openApp = new Intent(this, MainActivity.class)
				.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(
				this, 0, openApp, PendingIntent.FLAG_IMMUTABLE);
		Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(getString(R.string.notification_running))
				.setSmallIcon(R.drawable.ic_vpn)
				.setContentIntent(pendingIntent)
				.setOngoing(true)
				.build();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		} else {
			startForeground(1, notification);
		}
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return;
		}
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
		manager.createNotificationChannel(channel);
	}
}
