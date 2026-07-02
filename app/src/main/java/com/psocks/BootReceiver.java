package com.psocks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

import java.util.ArrayList;

public class BootReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			return;
		}
		SettingsStore settings = new SettingsStore(context);
		if (!settings.running()) {
			return;
		}
		Intent permissionIntent = VpnService.prepare(context);
		if (permissionIntent != null) {
			permissionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(permissionIntent);
			return;
		}
		Intent service = new Intent(context, ProxyVpnService.class)
				.setAction(ProxyVpnService.ACTION_CONNECT);
		service.putExtra(SettingsStore.EXTRA_PROXY_HOST, settings.proxyHost())
			.putExtra(SettingsStore.EXTRA_PROXY_UDP_HOST, settings.proxyUdpHost())
			.putExtra(SettingsStore.EXTRA_PROXY_PORT, settings.proxyPort())
			.putExtra(SettingsStore.EXTRA_PROXY_USER, settings.proxyUser())
			.putExtra(SettingsStore.EXTRA_PROXY_PASS, settings.proxyPassword())
			.putExtra(SettingsStore.EXTRA_DNS4, settings.dns4())
			.putExtra(SettingsStore.EXTRA_DNS6, settings.dns6())
			.putExtra(SettingsStore.EXTRA_IPV4, settings.ipv4Enabled())
			.putExtra(SettingsStore.EXTRA_IPV6, settings.ipv6Enabled())
			.putExtra(SettingsStore.EXTRA_UDP_OVER_TCP, settings.udpOverTcp())
			.putExtra(SettingsStore.EXTRA_REMOTE_DNS, settings.remoteDns())
			.putExtra(SettingsStore.EXTRA_ROUTE_MODE, settings.routeMode())
			.putExtra(SettingsStore.EXTRA_SELECTED_APPS,
				new ArrayList<>(settings.selectedApps()));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(service);
		} else {
			context.startService(service);
		}
	}
}
