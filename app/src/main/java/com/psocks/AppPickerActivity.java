package com.psocks;

import android.Manifest;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.LruCache;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppPickerActivity extends ListActivity {
	private SettingsStore settings;
	private AppAdapter adapter;
	private boolean changed;
	private ExecutorService loader;

	private interface SelectionListener {
		void onSelectionChanged();
	}

	private static final class AppEntry {
		final PackageInfo packageInfo;
		final String label;
		boolean selected;

		AppEntry(PackageInfo packageInfo, String label, boolean selected) {
			this.packageInfo = packageInfo;
			this.label = label;
			this.selected = selected;
		}
	}

	private static final class AppAdapter extends ArrayAdapter<AppEntry> {
		private final List<AppEntry> allItems = new ArrayList<>();
		private final List<AppEntry> visibleItems = new ArrayList<>();
		private final SelectionListener selectionListener;
		private final LruCache<String, Drawable> iconCache = new LruCache<>(200);
		private String filter = "";

		AppAdapter(Context context, SelectionListener selectionListener) {
			super(context, R.layout.appitem);
			this.selectionListener = selectionListener;
		}

		@Override
		public int getCount() {
			return visibleItems.size();
		}

		@Override
		public AppEntry getItem(int position) {
			return visibleItems.get(position);
		}

		@Override
		public void add(AppEntry entry) {
			allItems.add(entry);
			if (matches(entry)) {
				visibleItems.add(entry);
			}
			notifyDataSetChanged();
		}

		@Override
		public void sort(Comparator<? super AppEntry> comparator) {
			Collections.sort(allItems, comparator);
			applyFilter(filter);
		}

		List<AppEntry> allItems() {
			return allItems;
		}

		void setItems(List<AppEntry> items) {
			allItems.clear();
			allItems.addAll(items);
			applyFilter(filter);
		}

		void applyFilter(String value) {
			filter = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
			visibleItems.clear();
			for (AppEntry entry : allItems) {
				if (matches(entry)) {
					visibleItems.add(entry);
				}
			}
			notifyDataSetChanged();
		}

		private boolean matches(AppEntry entry) {
			return entry.selected
					|| filter.isEmpty()
					|| entry.label.toLowerCase(Locale.ROOT).contains(filter)
					|| entry.packageInfo.packageName.toLowerCase(Locale.ROOT).contains(filter);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				row = LayoutInflater.from(getContext()).inflate(R.layout.appitem, parent, false);
			}
			AppEntry entry = getItem(position);
			PackageManager pm = getContext().getPackageManager();
			ApplicationInfo app = entry.packageInfo.applicationInfo;
			String packageName = entry.packageInfo.packageName;
			Drawable icon = iconCache.get(packageName);
			if (icon == null) {
				icon = app.loadIcon(pm);
				iconCache.put(packageName, icon);
			}
			((ImageView) row.findViewById(R.id.icon)).setImageDrawable(icon);
			((TextView) row.findViewById(R.id.name)).setText(entry.label);
			((TextView) row.findViewById(R.id.package_name)).setText(entry.packageInfo.packageName);
			CheckBox checkBox = row.findViewById(R.id.checked);
			checkBox.setOnCheckedChangeListener(null);
			checkBox.setChecked(entry.selected);
			checkBox.setOnCheckedChangeListener((button, checked) -> {
				if (entry.selected != checked) {
					entry.selected = checked;
					selectionListener.onSelectionChanged();
				}
			});
			row.setOnClickListener(view -> {
				entry.selected = !entry.selected;
				checkBox.setChecked(entry.selected);
				selectionListener.onSelectionChanged();
			});
			return row;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_list);
		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		settings = new SettingsStore(this);
		loader = Executors.newSingleThreadExecutor();
		adapter = new AppAdapter(this, this::onSelectionChanged);
		setListAdapter(adapter);
		loadInstalledApps();

		findViewById(R.id.back).setOnClickListener(v -> finish());

		EditText search = findViewById(R.id.search);
		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				adapter.applyFilter(s.toString());
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
	}

	@Override
	protected void onStop() {
		if (changed) {
			applySelection();
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		if (loader != null) {
			loader.shutdownNow();
		}
		super.onDestroy();
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id) {
		// Row and checkbox clicks are handled directly in AppAdapter.getView().
	}

	private void loadInstalledApps() {
		loader.execute(() -> {
			final List<AppEntry> entries = buildAppEntries();
			runOnUiThread(() -> {
				if (isFinishing()) {
					return;
				}
				adapter.setItems(entries);
			});
		});
	}

	private List<AppEntry> buildAppEntries() {
		Set<String> selected = settings.selectedApps();
		PackageManager pm = getPackageManager();
		List<AppEntry> entries = new ArrayList<>();
		for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
			if (getPackageName().equals(info.packageName) || info.requestedPermissions == null) {
				continue;
			}
			boolean usesNetwork = false;
			for (String permission : info.requestedPermissions) {
				if (Manifest.permission.INTERNET.equals(permission)) {
					usesNetwork = true;
					break;
				}
			}
			if (!usesNetwork) {
				continue;
			}
			String label = info.applicationInfo.loadLabel(pm).toString();
			entries.add(new AppEntry(info, label, selected.contains(info.packageName)));
		}
		Collections.sort(entries, (left, right) -> {
			if (left.selected != right.selected) {
				return left.selected ? -1 : 1;
			}
			return left.label.compareToIgnoreCase(right.label);
		});
		return entries;
	}

	private void restartVpnWithLatestApps() {
		SettingsStore settings = new SettingsStore(this);
		Intent intent = new Intent(this, ProxyVpnService.class)
				.setAction(ProxyVpnService.ACTION_CONNECT);
		intent.putExtra(SettingsStore.EXTRA_PROXY_HOST, settings.proxyHost())
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
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}

	private void applySelection() {
		Set<String> packageNames = new HashSet<>();
		for (AppEntry app : adapter.allItems()) {
			if (app.selected) {
				packageNames.add(app.packageInfo.packageName);
			}
		}
		settings.saveApps(packageNames);
		if (!packageNames.isEmpty() && settings.routeMode() == SettingsStore.ROUTE_ALL_APPS) {
			settings.saveRouteMode(SettingsStore.ROUTE_SELECTED_APPS);
		}
		changed = false;
		if (settings.running()) {
			if (packageNames.isEmpty() && settings.routeMode() == SettingsStore.ROUTE_SELECTED_APPS) {
				stopVpn();
			} else {
				restartVpnWithLatestApps();
			}
		}
	}

	private void stopVpn() {
		Intent intent = new Intent(this, ProxyVpnService.class)
				.setAction(ProxyVpnService.ACTION_DISCONNECT);
		startService(intent);
	}

	private void onSelectionChanged() {
		changed = true;
		applySelection();
	}
}
