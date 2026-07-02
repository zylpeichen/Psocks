package com.psocks;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {
	private SettingsStore settings;
	private EditText proxyHost;
	private EditText proxyUdpHost;
	private EditText proxyPort;
	private EditText proxyUser;
	private EditText proxyPassword;
	private EditText dns4;
	private EditText dns6;
	private CheckBox udpOverTcp;
	private CheckBox remoteDns;
	private CheckBox ipv4;
	private CheckBox ipv6;
	private RadioGroup routeMode;
	private TextView statusText;
	private Button appButton;
	private Switch powerSwitch;
	private boolean bindingUi;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		settings = new SettingsStore(this);
		setContentView(R.layout.main);

		proxyHost = findViewById(R.id.proxy_host);
		proxyUdpHost = findViewById(R.id.proxy_udp_host);
		proxyPort = findViewById(R.id.proxy_port);
		proxyUser = findViewById(R.id.proxy_user);
		proxyPassword = findViewById(R.id.proxy_password);
		dns4 = findViewById(R.id.dns_ipv4);
		dns6 = findViewById(R.id.dns_ipv6);
		ipv4 = findViewById(R.id.ipv4);
		ipv6 = findViewById(R.id.ipv6);
		udpOverTcp = findViewById(R.id.udp_over_tcp);
		remoteDns = findViewById(R.id.remote_dns);
		routeMode = findViewById(R.id.route_mode);
		statusText = findViewById(R.id.status_text);
		appButton = findViewById(R.id.apps);
		powerSwitch = findViewById(R.id.power_switch);

		remoteDns.setOnClickListener(this);
		appButton.setOnClickListener(this);
		powerSwitch.setOnCheckedChangeListener((button, checked) -> {
			if (!bindingUi) {
				handlePowerSwitch(checked);
			}
		});
		routeMode.setOnCheckedChangeListener((group, checkedId) -> updateUiState());
		loadSettings();
	}

	@Override
	protected void onResume() {
		super.onResume();
		syncRunningState();
		updateUiState();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 10) {
			if (resultCode == RESULT_OK) {
				settings.setRunning(true);
				startProxyService(ProxyVpnService.ACTION_CONNECT);
				updateUiState();
			} else {
				settings.setRunning(false);
				updateUiState();
			}
		}
	}

	@Override
	public void onClick(View view) {
		if (view == remoteDns) {
			saveSettings();
			updateUiState();
		} else if (view == appButton) {
			startActivity(new Intent(this, AppPickerActivity.class));
		}
	}

	private void handlePowerSwitch(boolean turnOn) {
		if (turnOn) {
			if (!saveSettings()) {
				setPowerSwitchChecked(false);
				return;
			}
			if (selectedRouteMode() == SettingsStore.ROUTE_SELECTED_APPS
					&& settings.selectedApps().isEmpty()) {
				Toast.makeText(this, R.string.select_apps_required, Toast.LENGTH_SHORT).show();
				setPowerSwitchChecked(false);
				return;
			}
			if (requestVpnPermissionIfNeeded()) {
				setPowerSwitchChecked(true);
				return;
			} else {
				settings.setRunning(true);
				startProxyService(ProxyVpnService.ACTION_CONNECT);
			}
		} else {
			settings.setRunning(false);
			startProxyService(ProxyVpnService.ACTION_DISCONNECT);
		}
		updateUiState();
	}

	private void loadSettings() {
		proxyHost.setText(settings.proxyHost());
		proxyUdpHost.setText(settings.proxyUdpHost());
		proxyPort.setText(String.valueOf(settings.proxyPort()));
		proxyUser.setText(settings.proxyUser());
		proxyPassword.setText(settings.proxyPassword());
		dns4.setText(settings.dns4());
		dns6.setText(settings.dns6());
		ipv4.setChecked(settings.ipv4Enabled());
		ipv6.setChecked(settings.ipv6Enabled());
		udpOverTcp.setChecked(settings.udpOverTcp());
		remoteDns.setChecked(settings.remoteDns());
		int mode = settings.routeMode();
		if (mode == SettingsStore.ROUTE_SELECTED_APPS && settings.selectedApps().isEmpty()) {
			mode = SettingsStore.ROUTE_ALL_APPS;
		}
		if (mode == SettingsStore.ROUTE_ALL_APPS) {
			routeMode.check(R.id.route_all);
		} else if (mode == SettingsStore.ROUTE_EXCLUDED_APPS) {
			routeMode.check(R.id.route_exclude);
		} else {
			routeMode.check(R.id.route_selected);
		}
		updateUiState();
	}

	private boolean saveSettings() {
		String portValue = proxyPort.getText().toString().trim();
		int port;
		try {
			port = Integer.parseInt(portValue);
		} catch (NumberFormatException e) {
			proxyPort.setError(getString(R.string.invalid_port));
			return false;
		}
		if (port < 1 || port > 65535) {
			proxyPort.setError(getString(R.string.invalid_port));
			return false;
		}
		if (!ipv4.isChecked() && !ipv6.isChecked()) {
			ipv4.setChecked(true);
		}
		int route = selectedRouteMode();
		settings.saveConnection(
				proxyHost.getText().toString().trim(),
				proxyUdpHost.getText().toString().trim(),
				port,
				proxyUser.getText().toString(),
				proxyPassword.getText().toString(),
				dns4.getText().toString().trim(),
				dns6.getText().toString().trim(),
				ipv4.isChecked(),
				ipv6.isChecked(),
				udpOverTcp.isChecked(),
				remoteDns.isChecked(),
				route);
		return true;
	}

	private int selectedRouteMode() {
		int checked = routeMode.getCheckedRadioButtonId();
		if (checked == R.id.route_all) {
			return SettingsStore.ROUTE_ALL_APPS;
		}
		if (checked == R.id.route_exclude) {
			return SettingsStore.ROUTE_EXCLUDED_APPS;
		}
		return SettingsStore.ROUTE_SELECTED_APPS;
	}

	private void updateUiState() {
		boolean editable = !settings.running();
		proxyHost.setEnabled(editable);
		proxyUdpHost.setEnabled(editable);
		proxyPort.setEnabled(editable);
		proxyUser.setEnabled(editable);
		proxyPassword.setEnabled(editable);
		dns4.setEnabled(editable && !remoteDns.isChecked());
		dns6.setEnabled(editable && !remoteDns.isChecked());
		ipv4.setEnabled(editable);
		ipv6.setEnabled(editable);
		udpOverTcp.setEnabled(editable);
		remoteDns.setEnabled(editable);
		for (int i = 0; i < routeMode.getChildCount(); i++) {
			routeMode.getChildAt(i).setEnabled(editable);
		}
		appButton.setEnabled(selectedRouteMode() != SettingsStore.ROUTE_ALL_APPS);
		statusText.setText(settings.running() ? R.string.status_connected : R.string.status_idle);
		setPowerSwitchChecked(settings.running());
	}

	private void syncRunningState() {
		if (!isProxyServiceRunning()) {
			settings.setRunning(false);
		}
	}

	private boolean isProxyServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		if (manager == null) {
			return false;
		}
		List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
		for (ActivityManager.RunningServiceInfo service : services) {
			if (ProxyVpnService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private void setPowerSwitchChecked(boolean checked) {
		bindingUi = true;
		powerSwitch.setChecked(checked);
		bindingUi = false;
	}

	private boolean requestVpnPermissionIfNeeded() {
		Intent permissionIntent = VpnService.prepare(this);
		if (permissionIntent != null) {
			startActivityForResult(permissionIntent, 10);
			return true;
		}
		return false;
	}

	private Intent buildServiceIntent(String action) {
		Intent intent = new Intent(this, ProxyVpnService.class).setAction(action);
		if (ProxyVpnService.ACTION_CONNECT.equals(action)) {
			SettingsStore s = new SettingsStore(this);
			intent.putExtra(SettingsStore.EXTRA_PROXY_HOST, s.proxyHost())
				.putExtra(SettingsStore.EXTRA_PROXY_UDP_HOST, s.proxyUdpHost())
				.putExtra(SettingsStore.EXTRA_PROXY_PORT, s.proxyPort())
				.putExtra(SettingsStore.EXTRA_PROXY_USER, s.proxyUser())
				.putExtra(SettingsStore.EXTRA_PROXY_PASS, s.proxyPassword())
				.putExtra(SettingsStore.EXTRA_DNS4, s.dns4())
				.putExtra(SettingsStore.EXTRA_DNS6, s.dns6())
				.putExtra(SettingsStore.EXTRA_IPV4, s.ipv4Enabled())
				.putExtra(SettingsStore.EXTRA_IPV6, s.ipv6Enabled())
				.putExtra(SettingsStore.EXTRA_UDP_OVER_TCP, s.udpOverTcp())
				.putExtra(SettingsStore.EXTRA_REMOTE_DNS, s.remoteDns())
				.putExtra(SettingsStore.EXTRA_ROUTE_MODE, s.routeMode())
				.putExtra(SettingsStore.EXTRA_SELECTED_APPS,
					new ArrayList<>(s.selectedApps()));
		}
		return intent;
	}

	private void startProxyService(String action) {
		Intent intent = buildServiceIntent(action);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ProxyVpnService.ACTION_CONNECT.equals(action)) {
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}
}
