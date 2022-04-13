package space.cherryband.ari;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentActivity;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.Timer;
import java.util.TimerTask;

import itkach.slob.Slob;

public class LookupFragment extends BaseListFragment implements LookupListener {

    private Timer timer;
    private SearchView searchView;
    private AriApplication app;
    private SearchView.OnQueryTextListener queryTextListener;
    private SearchView.OnCloseListener closeListener;
    private MenuItemCompat.OnActionExpandListener openListener;

    private final static String TAG = LookupFragment.class.getSimpleName();

    @Override
    char getEmptyIcon() {
        return IconMaker.IC_SEARCH;
    }

    @Override
    CharSequence getEmptyText() {
        return "";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (AriApplication) getActivity().getApplication();
        app.addLookupListener(this);
    }

    @Override
    protected boolean supportsSelection() {
        return false;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setBusy(false);
        ListView listView = getListView();
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            Log.i("--", "Item clicked: " + position);
            Intent intent = new Intent(getActivity(),
                    ArticleCollectionActivity.class);
            intent.putExtra("position", position);
            startActivity(intent);
        });
        final AriApplication app = (AriApplication) getActivity().getApplication();
        getListView().setAdapter(app.lastResult);

        closeListener = () -> true;

        queryTextListener = new SearchView.OnQueryTextListener() {

            TimerTask scheduledLookup = null;

            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "query text submit: " + query);
                onQueryTextChange(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "new query text: " + newText);
                TimerTask doLookup = new TimerTask() {
                    @Override
                    public void run() {
                        final String query = searchView.getQuery().toString();
                        if (app.getLookupQuery().equals(query)) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> app.lookup(query));
                        scheduledLookup = null;
                    }
                };
                final String query = searchView.getQuery().toString();
                if (!app.getLookupQuery().equals(query)) {
                    if (scheduledLookup != null) {
                        scheduledLookup.cancel();
                    }
                    scheduledLookup = doLookup;
                    timer.schedule(doLookup, 600);
                }
                return true;
            }
        };

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_random){
            AriApplication app = (AriApplication) getActivity().getApplication();
            Slob.Blob blob = app.random();
            if (blob == null) {
                Toast.makeText(getContext(),
                        R.string.article_collection_nothing_found,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            Intent intent = new Intent(getActivity(), ArticleCollectionActivity.class);
            intent.setData(Uri.parse(app.getUrl(blob)));
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        timer = new Timer();
        inflater.inflate(R.menu.lookup, menu);
        MenuItem miFilter = menu.findItem(R.id.action_lookup);
        View filterActionView = miFilter.getActionView();
        searchView = (SearchView) filterActionView.findViewById(R.id.fldLookup);
        searchView.setQueryHint(miFilter.getTitle());
        searchView.setOnQueryTextListener(queryTextListener);
        searchView.setOnCloseListener(closeListener);
        searchView.setSubmitButtonEnabled(false);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        FragmentActivity activity = getActivity();
        super.onPrepareOptionsMenu(menu);
        if (app.getAutoPaste()) {
            CharSequence clipboard = Clipboard.take(activity);
            if (clipboard != null) {
                app.lookup(clipboard.toString(), false);
            }
        }
        CharSequence query = app.getLookupQuery();
        searchView.setQuery(query, true);
        if (app.lastResult.getCount() > 0) {
            searchView.clearFocus();
        }
        MenuItem miRandomArticle = menu.findItem(R.id.action_random);
        miRandomArticle.setIcon(IconMaker.actionBar(activity, IconMaker.IC_RELOAD));
    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (searchView != null) {
            String query = searchView.getQuery().toString();
            outState.putString("lookupQuery", query);
        }
    }

    private void setBusy(boolean busy) {
        setListShown(!busy);
        if (!busy) {
            TextView emptyText = emptyView.findViewById(R.id.empty_text);
            String msg = "";
            String query = app.getLookupQuery();
            if (query != null && !query.equals("")) {
                msg = getString(R.string.lookup_nothing_found);
            }
            emptyText.setText(msg);
        }
    }

    @Override
    public void onDestroy() {
        if (timer != null) {
            timer.cancel();
        }
        AriApplication app = (AriApplication) getActivity().getApplication();
        app.removeLookupListener(this);
        super.onDestroy();
    }

    @Override
    public void onLookupStarted(String query) {
        setBusy(true);
    }

    @Override
    public void onLookupFinished(String query) {
        setBusy(false);
    }

    @Override
    public void onLookupCanceled(String query) {
        setBusy(false);
    }

}
