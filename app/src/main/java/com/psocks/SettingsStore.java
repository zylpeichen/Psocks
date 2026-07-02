package com.psocks;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class SettingsStore {
	private static final String FILE_NAME = "psocks_settings";
	private static final String KEY_PROXY_HOST = "proxy_host";
	private static final String KEY_PROXY_UDP_HOST = "proxy_udp_host";
	private static final String KEY_PROXY_PORT = "proxy_port";
	private static final String KEY_PROXY_USER = "proxy_user";
	private static final String KEY_PROXY_PASS = "proxy_pass";
	private static final String KEY_DNS4 = "dns4";
	private static final String KEY_DNS6 = "dns6";
	private static final String KEY_IPV4 = "ipv4";
	private static final String KEY_IPV6 = "ipv6";
	private static final String KEY_ROUTE_MODE = "route_mode";
	private static final String KEY_UDP_OVER_TCP = "udp_over_tcp";
	private static final String KEY_REMOTE_DNS = "remote_dns";
	private static final String KEY_SELECTED_APPS = "selected_apps";
	private static final String KEY_RUNNING = "running";

	static final int ROUTE_SELECTED_APPS = 0;
	static final int ROUTE_ALL_APPS = 1;
	static final int ROUTE_EXCLUDED_APPS = 2;

	private final SharedPreferences prefs;

	SettingsStore(Context context) {
		prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
	}

	String proxyHost() {
		return prefs.getString(KEY_PROXY_HOST, "127.0.0.1");
	}

	String proxyUdpHost() {
		return prefs.getString(KEY_PROXY_UDP_HOST, "");
	}

	int proxyPort() {
		return prefs.getInt(KEY_PROXY_PORT, 1080);
	}

	String proxyUser() {
		return prefs.getString(KEY_PROXY_USER, "");
	}

	String proxyPassword() {
		return prefs.getString(KEY_PROXY_PASS, "");
	}

	String dns4() {
		return prefs.getString(KEY_DNS4, "8.8.8.8");
	}

	String dns6() {
		return prefs.getString(KEY_DNS6, "2001:4860:4860::8888");
	}

	boolean ipv4Enabled() {
		return prefs.getBoolean(KEY_IPV4, true);
	}

	boolean ipv6Enabled() {
		return prefs.getBoolean(KEY_IPV6, true);
	}

	boolean udpOverTcp() {
		return prefs.getBoolean(KEY_UDP_OVER_TCP, false);
	}

	boolean remoteDns() {
		return prefs.getBoolean(KEY_REMOTE_DNS, true);
	}

	int routeMode() {
		return prefs.getInt(KEY_ROUTE_MODE, ROUTE_ALL_APPS);
	}

	Set<String> selectedApps() {
		return new HashSet<>(prefs.getStringSet(KEY_SELECTED_APPS, new HashSet<String>()));
	}

	boolean running() {
		return prefs.getBoolean(KEY_RUNNING, false);
	}

	void saveConnection(String host, String udpHost, int port, String user, String password,
			String dns4, String dns6, boolean ipv4, boolean ipv6, boolean udpOverTcp,
			boolean remoteDns, int routeMode) {
		prefs.edit()
				.putString(KEY_PROXY_HOST, host)
				.putString(KEY_PROXY_UDP_HOST, udpHost)
				.putInt(KEY_PROXY_PORT, port)
				.putString(KEY_PROXY_USER, user)
				.putString(KEY_PROXY_PASS, password)
				.putString(KEY_DNS4, dns4)
				.putString(KEY_DNS6, dns6)
				.putBoolean(KEY_IPV4, ipv4)
				.putBoolean(KEY_IPV6, ipv6)
				.putBoolean(KEY_UDP_OVER_TCP, udpOverTcp)
				.putBoolean(KEY_REMOTE_DNS, remoteDns)
				.putInt(KEY_ROUTE_MODE, routeMode)
				.commit();
	}

	void saveApps(Set<String> packageNames) {
		prefs.edit().putStringSet(KEY_SELECTED_APPS, new HashSet<>(packageNames)).commit();
	}

	void saveRouteMode(int routeMode) {
		prefs.edit().putInt(KEY_ROUTE_MODE, routeMode).commit();
	}

	void setRunning(boolean running) {
		prefs.edit().putBoolean(KEY_RUNNING, running).commit();
	}

	String mappedDns() {
		return "198.18.0.2";
	}

	int tunnelMtu() {
		return 8500;
	}

	String tunnelIpv4Address() {
		return "198.18.0.1";
	}

	int tunnelIpv4Prefix() {
		return 32;
	}

	String tunnelIpv6Address() {
		return "fc00::1";
	}

	int tunnelIpv6Prefix() {
		return 128;
	}

	int taskStackSize() {
		return 81920;
	}

	// ---- Keys for Intent extras (cross-process config passing) ----

	static final String EXTRA_PROXY_HOST = KEY_PROXY_HOST;
	static final String EXTRA_PROXY_UDP_HOST = KEY_PROXY_UDP_HOST;
	static final String EXTRA_PROXY_PORT = KEY_PROXY_PORT;
	static final String EXTRA_PROXY_USER = KEY_PROXY_USER;
	static final String EXTRA_PROXY_PASS = KEY_PROXY_PASS;
	static final String EXTRA_DNS4 = KEY_DNS4;
	static final String EXTRA_DNS6 = KEY_DNS6;
	static final String EXTRA_IPV4 = KEY_IPV4;
	static final String EXTRA_IPV6 = KEY_IPV6;
	static final String EXTRA_UDP_OVER_TCP = KEY_UDP_OVER_TCP;
	static final String EXTRA_REMOTE_DNS = KEY_REMOTE_DNS;
	static final String EXTRA_ROUTE_MODE = KEY_ROUTE_MODE;
	static final String EXTRA_SELECTED_APPS = KEY_SELECTED_APPS;
}
