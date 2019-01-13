package com.aizoban.naitokenzai.presenters;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseBooleanArray;

import com.aizoban.naitokenzai.BuildConfig;
import com.aizoban.naitokenzai.controllers.QueryManager;
import com.aizoban.naitokenzai.controllers.databases.DatabaseService;
import com.aizoban.naitokenzai.controllers.events.FavouriteMangaDeleteEvent;
import com.aizoban.naitokenzai.models.databases.FavouriteManga;
import com.aizoban.naitokenzai.presenters.mapper.FavouriteMangaMapper;
import com.aizoban.naitokenzai.utils.SearchUtils;
import com.aizoban.naitokenzai.utils.wrappers.RequestWrapper;
import com.aizoban.naitokenzai.views.FavouriteMangaView;
import com.aizoban.naitokenzai.views.activities.MangaActivity;
import com.aizoban.naitokenzai.views.adapters.FavouriteMangaAdapter;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class FavouriteMangaPresenterImpl implements FavouriteMangaPresenter {
    public static final String TAG = FavouriteMangaPresenterImpl.class.getSimpleName();

    private static final String SEARCH_NAME_PARCELABLE_KEY = TAG + ":" + "SearchNameParcelableKey";

    private static final String POSITION_PARCELABLE_KEY = TAG + ":" + "PositionParcelableKey";

    private FavouriteMangaView mFavouriteMangaView;
    private FavouriteMangaMapper mFavouriteMangaMapper;
    private FavouriteMangaAdapter mFavouriteMangaAdapter;

    private String mSearchName;

    private Parcelable mPositionSavedState;

    private Subscription mQueryFavouriteMangaSubscription;
    private Subscription mSearchViewSubscription;
    private PublishSubject<Observable<String>> mSearchViewPublishSubject;

    public FavouriteMangaPresenterImpl(FavouriteMangaView favouriteMangaView, FavouriteMangaMapper favouriteMangaMapper) {
        mFavouriteMangaView = favouriteMangaView;
        mFavouriteMangaMapper = favouriteMangaMapper;

        mSearchName = "";
    }

    @Override
    public void initializeViews() {
        mFavouriteMangaView.initializeToolbar();
        mFavouriteMangaView.initializeListView();
        mFavouriteMangaView.initializeEmptyRelativeLayout();
    }

    @Override
    public void initializeSearch() {
        mSearchViewPublishSubject = PublishSubject.create();
        mSearchViewSubscription = Observable.switchOnNext(mSearchViewPublishSubject)
                .debounce(SearchUtils.TIMEOUT, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onCompleted() {
                        queryFavouriteMangaFromDatabase();
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onNext(String query) {
                        if (query != null) {
                            mSearchName = query;
                        }

                        onCompleted();
                    }
                });
    }

    @Override
    public void initializeDataFromDatabase() {
        mFavouriteMangaAdapter = new FavouriteMangaAdapter(mFavouriteMangaView.getContext());

        mFavouriteMangaMapper.registerAdapter(mFavouriteMangaAdapter);
    }

    @Override
    public void registerForEvents() {
        EventBus.getDefault().register(this);
    }

    public void onEventMainThread(FavouriteMangaDeleteEvent event) {
        if (event != null) {
            queryFavouriteMangaFromDatabase();
        }
    }

    @Override
    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onResume() {
        queryFavouriteMangaFromDatabase();
    }

    @Override
    public void saveState(Bundle outState) {
        if (mSearchName != null) {
            outState.putString(SEARCH_NAME_PARCELABLE_KEY, mSearchName);
        }
        if (mFavouriteMangaMapper.getPositionState() != null) {
            outState.putParcelable(POSITION_PARCELABLE_KEY, mFavouriteMangaMapper.getPositionState());
        }
    }

    @Override
    public void restoreState(Bundle savedState) {
        if (savedState.containsKey(SEARCH_NAME_PARCELABLE_KEY)) {
            mSearchName = savedState.getString(SEARCH_NAME_PARCELABLE_KEY);

            savedState.remove(SEARCH_NAME_PARCELABLE_KEY);
        }
        if (savedState.containsKey(POSITION_PARCELABLE_KEY)) {
            mPositionSavedState = savedState.getParcelable(POSITION_PARCELABLE_KEY);

            savedState.remove(POSITION_PARCELABLE_KEY);
        }
    }

    @Override
    public void destroyAllSubscriptions() {
        if (mQueryFavouriteMangaSubscription != null) {
            mQueryFavouriteMangaSubscription.unsubscribe();
            mQueryFavouriteMangaSubscription = null;
        }
        if (mSearchViewSubscription != null) {
            mSearchViewSubscription.unsubscribe();
            mSearchViewSubscription = null;
        }
    }

    @Override
    public void releaseAllResources() {
        if (mFavouriteMangaAdapter != null) {
            mFavouriteMangaAdapter.setCursor(null);
            mFavouriteMangaAdapter = null;
        }
    }

    @Override
    public void onFavouriteMangaClick(int position) {
        if (mFavouriteMangaAdapter != null) {
            FavouriteManga selectedFavouriteManga = (FavouriteManga) mFavouriteMangaAdapter.getItem(position);
            if (selectedFavouriteManga != null) {
                String mangaSource = selectedFavouriteManga.getSource();
                String mangaUrl = selectedFavouriteManga.getUrl();

                Intent mangaIntent = MangaActivity.constructOnlineMangaActivityIntent(mFavouriteMangaView.getContext(), new RequestWrapper(mangaSource, mangaUrl));
                mFavouriteMangaView.getContext().startActivity(mangaIntent);
            }
        }
    }

    @Override
    public void onQueryTextChange(String query) {
        if (mSearchViewPublishSubject != null) {
            mSearchViewPublishSubject.onNext(Observable.just(query));
        }
    }

    @Override
    public void onOptionToTop() {
        mFavouriteMangaView.scrollToTop();
    }

    @Override
    public void onOptionDelete() {
        if (mFavouriteMangaAdapter != null) {
            ArrayList<FavouriteManga> favouriteMangaToDelete = new ArrayList<FavouriteManga>();

            SparseBooleanArray checkedItems = mFavouriteMangaMapper.getCheckedItemPositions();
            for (int index = 0; index < mFavouriteMangaAdapter.getCount(); index++) {
                if (checkedItems.get(index)) {
                    FavouriteManga favouriteManga = (FavouriteManga)mFavouriteMangaAdapter.getItem(index);
                    favouriteMangaToDelete.add(favouriteManga);
                }
            }

            Intent startService = new Intent(mFavouriteMangaView.getContext(), DatabaseService.class);
            startService.putExtra(DatabaseService.INTENT_DELETE_FAVOURITE_MANGA, favouriteMangaToDelete);
            mFavouriteMangaView.getContext().startService(startService);
        }
    }

    @Override
    public void onOptionSelectAll() {
        mFavouriteMangaView.selectAll();
    }

    @Override
    public void onOptionClear() {
        mFavouriteMangaView.clear();
    }

    private void queryFavouriteMangaFromDatabase() {
        if (mQueryFavouriteMangaSubscription != null) {
            mQueryFavouriteMangaSubscription.unsubscribe();
            mQueryFavouriteMangaSubscription = null;
        }

        if (mSearchName != null) {
            mQueryFavouriteMangaSubscription = QueryManager
                    .queryFavouriteMangasFromName(mSearchName)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Cursor>() {
                        @Override
                        public void onCompleted() {
                            restorePosition();
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onNext(Cursor cursor) {
                            if (mFavouriteMangaAdapter != null) {
                                mFavouriteMangaAdapter.setCursor(cursor);
                            }

                            if (cursor != null && cursor.getCount() != 0) {
                                mFavouriteMangaView.hideEmptyRelativeLayout();
                            } else {
                                mFavouriteMangaView.showEmptyRelativeLayout();
                            }
                        }
                    });
        }
    }

    private void restorePosition() {
        if (mPositionSavedState != null) {
            mFavouriteMangaMapper.setPositionState(mPositionSavedState);

            mPositionSavedState = null;
        }
    }
}
