package com.aizoban.naitokenzai.presenters;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;

import com.aizoban.naitokenzai.BuildConfig;
import com.aizoban.naitokenzai.controllers.QueryManager;
import com.aizoban.naitokenzai.controllers.events.SearchCatalogueWrapperSubmitEvent;
import com.aizoban.naitokenzai.controllers.factories.DefaultFactory;
import com.aizoban.naitokenzai.models.Manga;
import com.aizoban.naitokenzai.presenters.mapper.CatalogueMapper;
import com.aizoban.naitokenzai.utils.SearchUtils;
import com.aizoban.naitokenzai.utils.wrappers.RequestWrapper;
import com.aizoban.naitokenzai.utils.wrappers.SearchCatalogueWrapper;
import com.aizoban.naitokenzai.views.CatalogueView;
import com.aizoban.naitokenzai.views.activities.MangaActivity;
import com.aizoban.naitokenzai.views.adapters.CatalogueAdapter;
import com.aizoban.naitokenzai.views.fragments.CatalogueFilterFragment;

import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class CataloguePresenterImpl implements CataloguePresenter {
    public static final String TAG = CataloguePresenterImpl.class.getSimpleName();

    private static final String POSITION_PARCELABLE_KEY = TAG + ":" + "PositionParcelableKey";

    private CatalogueView mCatalogueView;
    private CatalogueMapper mCatalogueMapper;
    private CatalogueAdapter mCatalogueAdapter;

    private SearchCatalogueWrapper mSearchCatalogueWrapper;

    private Parcelable mPositionSavedState;

    private Subscription mQueryCatalogueMangaSubscription;
    private Subscription mSearchViewSubscription;
    private PublishSubject<Observable<String>> mSearchViewPublishSubject;

    public CataloguePresenterImpl(CatalogueView catalogueView, CatalogueMapper catalogueMapper) {
        mCatalogueView = catalogueView;
        mCatalogueMapper = catalogueMapper;

        mSearchCatalogueWrapper = DefaultFactory.SearchCatalogueWrapper.constructDefault();
    }

    @Override
    public void initializeViews() {
        mCatalogueView.initializeToolbar();
        mCatalogueView.initializeGridView();
        mCatalogueView.initializeEmptyRelativeLayout();
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
                        queryCatalogueMangaFromPreferenceSource();
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onNext(String query) {
                        if (mSearchCatalogueWrapper != null) {
                            mSearchCatalogueWrapper.setNameArgs(query);
                        }

                        onCompleted();
                    }
                });
    }

    @Override
    public void initializeDataFromPreferenceSource() {
        mCatalogueAdapter = new CatalogueAdapter(mCatalogueView.getContext());

        mCatalogueMapper.registerAdapter(mCatalogueAdapter);

        queryCatalogueMangaFromPreferenceSource();
    }

    @Override
    public void registerForEvents() {
        EventBus.getDefault().register(this);
    }

    public void onEventMainThread(SearchCatalogueWrapperSubmitEvent event) {
        if (event != null && event.getSearchCatalogueWrapper() != null) {
            mSearchCatalogueWrapper = event.getSearchCatalogueWrapper();

            queryCatalogueMangaFromPreferenceSource();
        }
    }

    @Override
    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void saveState(Bundle outState) {
        if (mSearchCatalogueWrapper != null) {
            outState.putParcelable(SearchCatalogueWrapper.PARCELABLE_KEY, mSearchCatalogueWrapper);
        }
        if (mCatalogueMapper.getPositionState() != null) {
            outState.putParcelable(POSITION_PARCELABLE_KEY, mCatalogueMapper.getPositionState());
        }
    }

    @Override
    public void restoreState(Bundle savedState) {
        if (savedState.containsKey(SearchCatalogueWrapper.PARCELABLE_KEY)) {
            mSearchCatalogueWrapper = savedState.getParcelable(SearchCatalogueWrapper.PARCELABLE_KEY);

            savedState.remove(SearchCatalogueWrapper.PARCELABLE_KEY);
        }
        if (savedState.containsKey(POSITION_PARCELABLE_KEY)) {
            mPositionSavedState = savedState.getParcelable(POSITION_PARCELABLE_KEY);

            savedState.remove(POSITION_PARCELABLE_KEY);
        }
    }

    @Override
    public void destroyAllSubscriptions() {
        if (mQueryCatalogueMangaSubscription != null) {
            mQueryCatalogueMangaSubscription.unsubscribe();
            mQueryCatalogueMangaSubscription = null;
        }
        if (mSearchViewSubscription != null) {
            mSearchViewSubscription.unsubscribe();
            mSearchViewSubscription = null;
        }
    }

    @Override
    public void releaseAllResources() {
        if (mCatalogueAdapter != null) {
            mCatalogueAdapter.setCursor(null);
            mCatalogueAdapter = null;
        }
    }

    @Override
    public void onMangaClick(int position) {
        if (mCatalogueAdapter != null) {
            Manga selectedManga = (Manga) mCatalogueAdapter.getItem(position);
            if (selectedManga != null) {
                String mangaSource = selectedManga.getSource();
                String mangaUrl = selectedManga.getUrl();

                Intent mangaIntent = MangaActivity.constructOnlineMangaActivityIntent(mCatalogueView.getContext(), new RequestWrapper(mangaSource, mangaUrl));
                mCatalogueView.getContext().startActivity(mangaIntent);
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
    public void onOptionFilter() {
        if (((FragmentActivity)mCatalogueView.getContext()).getSupportFragmentManager().findFragmentByTag(CatalogueFilterFragment.TAG) == null) {
            CatalogueFilterFragment catalogueFilterFragment = CatalogueFilterFragment.newInstance(mSearchCatalogueWrapper);

            catalogueFilterFragment.show(((FragmentActivity)mCatalogueView.getContext()).getSupportFragmentManager(), CatalogueFilterFragment.TAG);
        }
    }

    @Override
    public void onOptionToTop() {
        mCatalogueView.scrollToTop();
    }

    private void queryCatalogueMangaFromPreferenceSource() {
        if (mQueryCatalogueMangaSubscription != null) {
            mQueryCatalogueMangaSubscription.unsubscribe();
            mQueryCatalogueMangaSubscription = null;
        }

        if (mSearchCatalogueWrapper != null) {
            mQueryCatalogueMangaSubscription = QueryManager
                    .queryCatalogueMangasFromPreferenceSource(mSearchCatalogueWrapper)
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
                            if (mCatalogueAdapter != null) {
                                mCatalogueAdapter.setCursor(cursor);
                            }

                            if (cursor != null && cursor.getCount() != 0) {
                                mCatalogueView.hideEmptyRelativeLayout();
                            } else {
                                mCatalogueView.showEmptyRelativeLayout();
                            }
                        }
                    });
        }
    }

    private void restorePosition() {
        if (mPositionSavedState != null) {
            mCatalogueMapper.setPositionState(mPositionSavedState);

            mPositionSavedState = null;
        }
    }
}
