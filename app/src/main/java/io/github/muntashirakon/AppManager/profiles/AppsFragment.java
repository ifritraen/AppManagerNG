// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.main.MainListOptions;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        MenuProvider, AdvancedSearchView.OnQueryTextListener, ListOptions.ListOptionActions {

    public static class AppsFragmentItem {
        @NonNull
        public final String packageName;
        @Nullable
        public CharSequence label;
        public ApplicationInfo applicationInfo;
        public IFilterableAppInfo filterableAppInfo;
        public long firstInstallTime;
        public long lastUpdateTime;

        public AppsFragmentItem(@NonNull String packageName) {
            this.packageName = packageName;
        }

        public long getFirstInstallTime() {
            if (firstInstallTime > 0) return firstInstallTime;
            if (filterableAppInfo != null) return filterableAppInfo.getFirstInstallTime();
            return 0;
        }

        public long getLastUpdateTime() {
            if (lastUpdateTime > 0) return lastUpdateTime;
            if (filterableAppInfo != null) return filterableAppInfo.getLastUpdateTime();
            return 0;
        }

        public boolean isSystemApp() {
            if (applicationInfo != null) return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (filterableAppInfo != null) return filterableAppInfo.isSystemApp();
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof String) {
                return Objects.equals(packageName, o);
            }
            if (!(o instanceof AppsFragmentItem)) return false;
            AppsFragmentItem that = (AppsFragmentItem) o;
            return Objects.equals(packageName, that.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName);
        }
    }

    private AppsBaseProfileActivity mActivity;
    private SwipeRefreshLayout mSwipeRefresh;
    private LinearProgressIndicator mProgressIndicator;
    private AppsProfileViewModel mModel;
    private AppsRecyclerAdapter mAdapter;
    private TextView mEmptyTitle;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (AppsBaseProfileActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = view.findViewById(R.id.scrollView);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(mActivity));
        View emptyView = view.findViewById(android.R.id.empty);
        if (emptyView != null) {
            mEmptyTitle = emptyView.findViewById(R.id.empty_state_title);
            if (mEmptyTitle != null) {
                mEmptyTitle.setText(R.string.no_apps);
            } else if (emptyView instanceof TextView) {
                mEmptyTitle = (TextView) emptyView;
                mEmptyTitle.setText(R.string.no_apps);
            }
            View emptySummary = emptyView.findViewById(R.id.empty_state_summary);
            if (emptySummary != null) {
                emptySummary.setVisibility(View.GONE);
            }
            View emptyAction = emptyView.findViewById(R.id.empty_state_action);
            if (emptyAction != null) {
                emptyAction.setVisibility(View.GONE);
            }
            recyclerView.setEmptyView(emptyView);
        }
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        view.findViewById(R.id.alert_text).setVisibility(View.GONE);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        mModel = mActivity.model;
        mAdapter = new AppsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        mModel.getPackages().observe(getViewLifecycleOwner(), packages -> {
            mProgressIndicator.hide();
            mAdapter.setList(packages);
        });
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_apps_profile_actions, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            AdvancedSearchView searchView = (AdvancedSearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setOnQueryTextListener(this);
            }
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                    if (mAdapter != null) {
                        mAdapter.filter("", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_sort) {
            ProfileAppsListOptions listOptions = new ProfileAppsListOptions();
            listOptions.setListOptionActions(this);
            listOptions.show(getChildFragmentManager(), ProfileAppsListOptions.TAG);
            return true;
        }
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText, @AdvancedSearchView.SearchType int type) {
        if (mAdapter != null) {
            mAdapter.filter(newText, type);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, @AdvancedSearchView.SearchType int type) {
        return false;
    }

    @Override
    public void setSortBy(int sortBy) {
        if (mAdapter != null) {
            mAdapter.setSortBy(sortBy);
        }
    }

    @Override
    public int getSortBy() {
        return mAdapter != null ? mAdapter.getSortBy() : Prefs.MainPage.getSortOrder();
    }

    @Override
    public void setReverseSort(boolean reverseSort) {
        if (mAdapter != null) {
            mAdapter.setReverseSort(reverseSort);
        }
    }

    @Override
    public boolean isReverseSort() {
        return mAdapter != null ? mAdapter.isReverseSort() : Prefs.MainPage.isReverseSort();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.getSupportActionBar() != null) {
            mActivity.getSupportActionBar().setSubtitle(mModel.getPreviewTitleString());
        }
        mActivity.fab.show();
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        mModel.loadPackages();
    }

    public static void sortItems(List<AppsFragmentItem> items, int sortBy, boolean reverse) {
        if (items == null || items.size() <= 1) return;
        Collator collator = Collator.getInstance();
        int mode = reverse ? -1 : 1;
        Collections.sort(items, (o1, o2) -> {
            switch (sortBy) {
                case MainListOptions.SORT_BY_PACKAGE_NAME: {
                    return mode * o1.packageName.compareTo(o2.packageName);
                }
                case MainListOptions.SORT_BY_DOMAIN: {
                    boolean sys1 = o1.isSystemApp();
                    boolean sys2 = o2.isSystemApp();
                    int comp = Boolean.compare(sys1, sys2);
                    if (comp != 0) return mode * comp;
                    CharSequence l1 = o1.label != null ? o1.label : o1.packageName;
                    CharSequence l2 = o2.label != null ? o2.label : o2.packageName;
                    return collator.compare(l1.toString(), l2.toString());
                }
                case MainListOptions.SORT_BY_INSTALLATION_DATE: {
                    int comp = Long.compare(o1.getFirstInstallTime(), o2.getFirstInstallTime());
                    if (comp != 0) return -mode * comp;
                    CharSequence l1 = o1.label != null ? o1.label : o1.packageName;
                    CharSequence l2 = o2.label != null ? o2.label : o2.packageName;
                    return collator.compare(l1.toString(), l2.toString());
                }
                case MainListOptions.SORT_BY_LAST_UPDATE: {
                    int comp = Long.compare(o1.getLastUpdateTime(), o2.getLastUpdateTime());
                    if (comp != 0) return -mode * comp;
                    CharSequence l1 = o1.label != null ? o1.label : o1.packageName;
                    CharSequence l2 = o2.label != null ? o2.label : o2.packageName;
                    return collator.compare(l1.toString(), l2.toString());
                }
                case MainListOptions.SORT_BY_APP_LABEL:
                default: {
                    CharSequence l1 = o1.label != null ? o1.label : o1.packageName;
                    CharSequence l2 = o2.label != null ? o2.label : o2.packageName;
                    return mode * collator.compare(l1.toString(), l2.toString());
                }
            }
        });
    }

    public static boolean matchesQuery(AppsFragmentItem item, String query, @AdvancedSearchView.SearchType int searchType) {
        if (TextUtils.isEmpty(query)) return true;
        String q = query.toLowerCase(Locale.ROOT);
        String label = item.label != null ? item.label.toString().toLowerCase(Locale.ROOT) : "";
        String pkg = item.packageName.toLowerCase(Locale.ROOT);

        switch (searchType) {
            case AdvancedSearchView.SEARCH_TYPE_PREFIX:
                return label.startsWith(q) || pkg.startsWith(q);
            case AdvancedSearchView.SEARCH_TYPE_SUFFIX:
                return label.endsWith(q) || pkg.endsWith(q);
            case AdvancedSearchView.SEARCH_TYPE_REGEX:
                try {
                    Pattern p = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
                    return p.matcher(label).find() || p.matcher(pkg).find();
                } catch (Exception e) {
                    return label.contains(q) || pkg.contains(q);
                }
            case AdvancedSearchView.SEARCH_TYPE_CONTAINS:
            default:
                return label.contains(q) || pkg.contains(q);
        }
    }

    public class AppsRecyclerAdapter extends RecyclerView.Adapter<AppsRecyclerAdapter.ViewHolder> implements Filterable {
        final PackageManager pm;
        final ArrayList<AppsFragmentItem> packages = new ArrayList<>();
        private final ArrayList<AppsFragmentItem> mOriginalList = new ArrayList<>();
        final ImageLoader.DefaultImageDrawable defaultImage;
        private String mQuery = "";
        @AdvancedSearchView.SearchType
        private int mSearchType = AdvancedSearchView.SEARCH_TYPE_CONTAINS;
        private int mSortBy = Prefs.MainPage.getSortOrder();
        private boolean mReverseSort = Prefs.MainPage.isReverseSort();
        private Filter mFilter;

        private AppsRecyclerAdapter() {
            pm = mActivity.getPackageManager();
            defaultImage = new ImageLoader.DefaultImageDrawable("android_default_icon", pm.getDefaultActivityIcon());
        }

        void setList(List<AppsFragmentItem> list) {
            mOriginalList.clear();
            if (list != null) {
                mOriginalList.addAll(list);
            }
            filterAndSort();
        }

        public void setSortBy(int sortBy) {
            if (mSortBy != sortBy) {
                mSortBy = sortBy;
                Prefs.MainPage.setSortOrder(sortBy);
                filterAndSort();
            }
        }

        public int getSortBy() {
            return mSortBy;
        }

        public void setReverseSort(boolean reverseSort) {
            if (mReverseSort != reverseSort) {
                mReverseSort = reverseSort;
                Prefs.MainPage.setReverseSort(reverseSort);
                filterAndSort();
            }
        }

        public boolean isReverseSort() {
            return mReverseSort;
        }

        public void filter(String query, @AdvancedSearchView.SearchType int searchType) {
            mQuery = query != null ? query : "";
            mSearchType = searchType;
            filterAndSort();
        }

        private void filterAndSort() {
            getFilter().filter(mQuery);
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null) {
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        String query = constraint != null ? constraint.toString() : "";
                        List<AppsFragmentItem> filtered = new ArrayList<>();
                        for (AppsFragmentItem item : mOriginalList) {
                            if (matchesQuery(item, query, mSearchType)) {
                                filtered.add(item);
                            }
                        }
                        sortItems(filtered, mSortBy, mReverseSort);
                        FilterResults results = new FilterResults();
                        results.values = filtered;
                        results.count = filtered.size();
                        return results;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        List<AppsFragmentItem> newItems = (List<AppsFragmentItem>) results.values;
                        if (newItems != null) {
                            AdapterUtils.notifyDataSetChanged(AppsRecyclerAdapter.this, packages, newItems);
                        }
                        if (mEmptyTitle != null) {
                            mEmptyTitle.setText(!TextUtils.isEmpty(mQuery)
                                    ? R.string.no_matching_package_found
                                    : R.string.no_apps);
                        }
                    }
                };
            }
            return mFilter;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppsFragmentItem fragmentItem = packages.get(position);
            holder.icon.setTag(fragmentItem);
            if (fragmentItem.applicationInfo != null) {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, fragmentItem.applicationInfo, holder.icon);
            } else if (fragmentItem.filterableAppInfo != null) {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, holder.icon, tag ->
                        new ImageLoader.ImageFetcherResult(tag, UIUtils.getBitmapFromDrawable(fragmentItem.filterableAppInfo.getAppIcon()), true, true, defaultImage));
            } else {
                holder.icon.setImageBitmap(defaultImage.getImage());
            }
            CharSequence label = fragmentItem.label;
            holder.title.setText(label != null ? label : fragmentItem.packageName);
            if (label != null) {
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText(fragmentItem.packageName);
            } else {
                holder.subtitle.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> {
            });
            holder.itemView.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(mActivity, holder.itemView);
                popupMenu.setForceShowIcon(true);
                popupMenu.getMenu().add(R.string.delete).setIcon(R.drawable.ic_trash_can)
                        .setOnMenuItemClickListener(item -> {
                            mModel.deletePackage(fragmentItem.packageName);
                            return true;
                        });
                popupMenu.show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return packages.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title;
            TextView subtitle;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(android.R.id.icon);
                icon.setContentDescription(itemView.getContext().getString(R.string.icon));
                title = itemView.findViewById(android.R.id.title);
                subtitle = itemView.findViewById(android.R.id.summary);
            }
        }
    }
}
