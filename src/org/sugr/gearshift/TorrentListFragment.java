package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.HashSet;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.Spanned;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A list fragment representing a list of Torrents. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link TorrentDetailFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class TorrentListFragment extends ListFragment {

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sDummyCallbacks;

    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    private boolean mAltSpeed = false;

    private boolean mRefreshing = true;

    private boolean mIsCABDestroyed = false;

    private int mChoiceMode = ListView.CHOICE_MODE_NONE;

    private TransmissionProfileListAdapter mProfileAdapter;
    private TorrentListAdapter mTorrentListAdapter;

    private TransmissionProfile mCurrentProfile;
    private TransmissionSession mSession;
    private TransmissionSessionStats mSessionStats;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        public void onItemSelected(Torrent torrent);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(Torrent torrent) {
        }
    };

    private LoaderCallbacks<TransmissionProfile[]> mProfileLoaderCallbacks = new LoaderCallbacks<TransmissionProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TransmissionProfile[]> onCreateLoader(
                int id, Bundle args) {
            return new TransmissionProfileSupportLoader(getActivity());
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TransmissionProfile[]> loader,
                TransmissionProfile[] profiles) {

            mProfileAdapter.clear();
            if (profiles.length > 0) {
                mProfileAdapter.addAll(profiles);
            } else {
                mProfileAdapter.add(TransmissionProfileListAdapter.EMPTY_PROFILE);
                setEmptyText(R.string.no_profiles_empty_list);
                mRefreshing = false;
                getActivity().invalidateOptionsMenu();
            }

            String currentId = TransmissionProfile.getCurrentProfileId(getActivity());
            int index = 0;
            for (TransmissionProfile prof : profiles) {
                if (prof.getId().equals(currentId)) {
                    ActionBar actionBar = getActivity().getActionBar();
                    if (actionBar != null)
                        actionBar.setSelectedNavigationItem(index);
                    mCurrentProfile = prof;
                    break;
                }
                index++;
            }

            if (mCurrentProfile == null && profiles.length > 0)
                mCurrentProfile = profiles[0];
            getActivity().getSupportLoaderManager().initLoader(TorrentListActivity.SESSION_LOADER_ID, null, mTorrentLoaderCallbacks);
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TransmissionProfile[]> loader) {
            mProfileAdapter.clear();
        }

    };

    private LoaderCallbacks<TransmissionSessionData> mTorrentLoaderCallbacks = new LoaderCallbacks<TransmissionSessionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionSessionData> onCreateLoader(
                int id, Bundle args) {
            TorrentListActivity.logD("Starting the torrents loader with profile " + mCurrentProfile);
            if (mCurrentProfile == null) return null;

            TransmissionSessionLoader loader = new TransmissionSessionLoader(getActivity(), mCurrentProfile);
            loader.setCurrentTorrents(((TransmissionSessionInterface) getActivity()).getCurrentTorrents());

            return loader;
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TransmissionSessionData> loader,
                TransmissionSessionData data) {

            if (data.session != null)
                mSession = data.session;
            if (data.stats != null)
                mSessionStats = data.stats;

            boolean invalidateMenu = false;
            if (mAltSpeed != mSession.isAltSpeedEnabled()) {
                mAltSpeed = mSession.isAltSpeedEnabled();
                invalidateMenu = true;
            }

            if (data.torrents.size() > 0 || mTorrentListAdapter.getCount() > 0) {
                /* The notifyDataSetChanged method sets this to true */
                mTorrentListAdapter.setNotifyOnChange(false);
                mTorrentListAdapter.clear();
                mTorrentListAdapter.addAll(data.torrents);
                mTorrentListAdapter.notifyDataSetChanged();

                ((TransmissionSessionInterface) getActivity()).setTorrents(data.torrents);

                FragmentManager manager = getActivity().getSupportFragmentManager();
                TorrentListMenuFragment menu = (TorrentListMenuFragment) manager.findFragmentById(R.id.torrent_list_menu);
                if (menu != null) {
                    menu.notifyTorrentListUpdate(data.torrents, data.session);
                }
                if (data.hasRemoved || data.hasAdded) {
                    TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                            TorrentDetailFragment.TAG);
                    if (detail != null) {
                        detail.notifyTorrentListChanged(data.hasRemoved, data.hasAdded);
                    }
                }
            }

            if (mTorrentListAdapter.getCount() == 0) {
                ((TransmissionSessionInterface) getActivity()).setTorrents(null);
                setEmptyText(R.string.no_torrents_empty_list);
            }

            if (mRefreshing) {
                mRefreshing = false;
                invalidateMenu = true;
            }
            if (invalidateMenu)
                getActivity().invalidateOptionsMenu();
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TransmissionSessionData> loader) {
        }

    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTorrentListAdapter = new TorrentListAdapter(getActivity());
        setListAdapter(mTorrentListAdapter);

        setHasOptionsMenu(true);
        getActivity().requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        getActivity().setProgressBarIndeterminateVisibility(true);

        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            mProfileAdapter = new TransmissionProfileListAdapter(getActivity());

            actionBar.setListNavigationCallbacks(mProfileAdapter, new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int pos, long id) {
                    TransmissionProfile profile = mProfileAdapter.getItem(pos);
                    if (profile != TransmissionProfileListAdapter.EMPTY_PROFILE)
                        TransmissionProfile.setCurrentProfile(profile, getActivity());

                    return false;
                }
            });

            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        }

        getActivity().getSupportLoaderManager().initLoader(TorrentListActivity.PROFILES_LOADER_ID, null, mProfileLoaderCallbacks);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final ListView list = getListView();
        list.setChoiceMode(mChoiceMode);
        list.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {

                if (!((TorrentListActivity) getActivity()).isDetailsPanelShown()) {
                    list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                    mIsCABDestroyed = false;
                    setActivatedPosition(position);
                    return true;
                }
                return false;
            }});

        list.setMultiChoiceModeListener(new MultiChoiceModeListener() {
            private HashSet<Integer> mSelectedTorrentIds;

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                final Loader<TransmissionSessionData> loader;
                final int[] ids = new int[mSelectedTorrentIds.size()];
                int index = 0;
                for (Integer id : mSelectedTorrentIds)
                    ids[index++] = id;

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.no, null);

                switch (item.getItemId()) {
                    case R.id.remove:
                        loader = getActivity().getSupportLoaderManager()
                            .getLoader(TorrentListActivity.SESSION_LOADER_ID);
                        if (loader != null) {
                            builder.setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    ((TransmissionSessionLoader) loader).setTorrentsRemove(ids, false);
                                }
                            })
                                .setMessage(R.string.remove_selected_confirmation)
                                .show();
                        }
                        mode.finish();
                        break;
                    case R.id.delete:
                        loader = getActivity().getSupportLoaderManager()
                            .getLoader(TorrentListActivity.SESSION_LOADER_ID);
                        if (loader != null) {
                            builder.setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    ((TransmissionSessionLoader) loader).setTorrentsRemove(ids, true);
                                }
                            })
                                .setMessage(R.string.delete_selected_confirmation)
                                .show();
                        }
                        mode.finish();
                        break;
                    case R.id.resume:
                        loader = getActivity().getSupportLoaderManager()
                            .getLoader(TorrentListActivity.SESSION_LOADER_ID);
                        if (loader != null) {
                            ((TransmissionSessionLoader) loader).setTorrentsAction("torrent-start", ids);
                        }
                        mode.finish();
                        break;
                    case R.id.pause:
                        loader = getActivity().getSupportLoaderManager()
                            .getLoader(TorrentListActivity.SESSION_LOADER_ID);
                        if (loader != null) {
                            ((TransmissionSessionLoader) loader).setTorrentsAction("torrent-stop", ids);
                        }
                        mode.finish();
                        break;
                }
                return true;
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.torrent_list_multiselect, menu);

                mSelectedTorrentIds = new HashSet<Integer>();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                TorrentListActivity.logD("Destroying context menu");
                mIsCABDestroyed = true;
                mSelectedTorrentIds = null;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode,
                    int position, long id, boolean checked) {

                if (checked)
                    mSelectedTorrentIds.add(mTorrentListAdapter.getItem(position).getId());
                else
                    mSelectedTorrentIds.remove(mTorrentListAdapter.getItem(position).getId());

                ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
                boolean hasPaused = false;
                boolean hasRunning = false;
                for (Torrent t : torrents) {
                    if (mSelectedTorrentIds.contains(t.getId())) {
                        if (t.getStatus() == Torrent.Status.STOPPED) {
                            hasPaused = true;
                        } else {
                            hasRunning = true;
                        }
                    }
                }
                Menu menu = mode.getMenu();
                MenuItem item = menu.findItem(R.id.resume);
                item.setVisible(hasPaused).setEnabled(hasPaused);

                item = menu.findItem(R.id.pause);
                item.setVisible(hasRunning).setEnabled(hasRunning);
            }});
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        if (mIsCABDestroyed)
            listView.setChoiceMode(mChoiceMode);

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        /* TODO: replace with real torrent */
        mCallbacks.onItemSelected(mTorrentListAdapter.getItem(position));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_torrent_list, container, false);

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.torrent_list_options, menu);

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (mRefreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);

        item = menu.findItem(R.id.menu_alt_speed);
        if (mAltSpeed) {
            item.setIcon(R.drawable.ic_menu_alt_speed_on);
            item.setTitle(R.string.alt_speed_label_off);
        } else {
            item.setIcon(R.drawable.ic_menu_alt_speed_off);
            item.setTitle(R.string.alt_speed_label_on);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Loader<TransmissionSessionData> loader;
        switch (item.getItemId()) {
            case R.id.menu_alt_speed:
                loader = getActivity().getSupportLoaderManager()
                    .getLoader(TorrentListActivity.SESSION_LOADER_ID);
                if (loader != null) {
                    mAltSpeed = !mAltSpeed;
                    mSession.setAltSpeedEnabled(mAltSpeed);
                    ((TransmissionSessionLoader) loader).setSession(mSession, "alt-speed-enabled");
                    getActivity().invalidateOptionsMenu();
                }
                return true;
            case R.id.menu_refresh:
                loader = getActivity().getSupportLoaderManager()
                    .getLoader(TorrentListActivity.SESSION_LOADER_ID);
                if (loader != null) {
                    loader.onContentChanged();
                    mRefreshing = !mRefreshing;
                    getActivity().invalidateOptionsMenu();
                }
                return true;
            case R.id.menu_settings:
                Intent i = new Intent(getActivity(), SettingsActivity.class);
                getActivity().startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setEmptyText(int stringId) {
        Spanned text = Html.fromHtml(getString(stringId));

        ((TextView) getListView().getEmptyView()).setText(text);
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        mChoiceMode = activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE;
        getListView().setChoiceMode(mChoiceMode);
    }

    private void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

    private static class TransmissionProfileListAdapter extends ArrayAdapter<TransmissionProfile> {
        public static final TransmissionProfile EMPTY_PROFILE = new TransmissionProfile();

        public TransmissionProfileListAdapter(Context context) {
            super(context, 0);

            add(EMPTY_PROFILE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            TransmissionProfile profile = getItem(position);

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector, null);
            }

            TextView name = (TextView) rowView.findViewById(R.id.name);
            TextView summary = (TextView) rowView.findViewById(R.id.summary);

            if (profile == EMPTY_PROFILE) {
                name.setText(R.string.no_profiles);
                if (summary != null)
                    summary.setText(R.string.create_profile_in_settings);
            } else {
                name.setText(profile.getName());
                if (summary != null)
                    summary.setText((profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                        + profile.getHost() + ":" + profile.getPort());
            }

            return rowView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector_dropdown, null);
            }

            return getView(position, rowView, parent);
        }
    }

    private class TorrentListAdapter extends ArrayAdapter<Torrent> {

        public TorrentListAdapter(Context context) {
            super(context, R.layout.torrent_list_item, R.id.name);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            Torrent torrent = getItem(position);

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_list_item, parent, false);
            }

            TextView name = (TextView) rowView.findViewById(R.id.name);

            TextView traffic = (TextView) rowView.findViewById(R.id.traffic);
            ProgressBar progress = (ProgressBar) rowView.findViewById(R.id.progress);
            TextView status = (TextView) rowView.findViewById(R.id.status);

            name.setText(torrent.getName());

            if (torrent.getMetadataPercentComplete() < 1) {
                progress.setSecondaryProgress((int) (torrent.getMetadataPercentComplete() * 100));
            } else if (torrent.getPercentDone() < 1) {
                progress.setSecondaryProgress((int) (torrent.getPercentDone() * 100));
            } else {
                progress.setSecondaryProgress(100);

                float limit = torrent.getActiveSeedRatioLimit();
                float current = torrent.getUploadRatio();

                if (limit == -1) {
                    progress.setProgress(100);
                } else {
                    if (current >= limit) {
                        progress.setProgress(100);
                    } else {
                        progress.setProgress((int) (current / limit * 100));
                    }
                }
            }

            traffic.setText(torrent.getTrafficText());
            status.setText(torrent.getStatusText());

            int color;
            switch(torrent.getStatus()) {
                case Torrent.Status.STOPPED:
                case Torrent.Status.CHECK_WAITING:
                case Torrent.Status.DOWNLOAD_WAITING:
                case Torrent.Status.SEED_WAITING:
                    color = getContext().getResources().getColor(android.R.color.darker_gray);
                    break;
                default:
                    color = getContext().getResources().getColor(android.R.color.primary_text_light);
                    break;
            }
            name.setTextColor(color);
            traffic.setTextColor(color);
            status.setTextColor(color);

            return rowView;
        }
    }
}
