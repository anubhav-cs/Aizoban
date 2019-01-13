package com.aizoban.naitokenzai.presenters;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;
import android.util.Pair;

import com.aizoban.naitokenzai.BuildConfig;
import com.aizoban.naitokenzai.controllers.NaitoKenzaiManager;
import com.aizoban.naitokenzai.controllers.QueryManager;
import com.aizoban.naitokenzai.controllers.factories.DefaultFactory;
import com.aizoban.naitokenzai.models.Chapter;
import com.aizoban.naitokenzai.models.Manga;
import com.aizoban.naitokenzai.models.databases.RecentChapter;
import com.aizoban.naitokenzai.presenters.mapper.MangaMapper;
import com.aizoban.naitokenzai.utils.wrappers.RequestWrapper;
import com.aizoban.naitokenzai.views.MangaView;
import com.aizoban.naitokenzai.views.activities.ChapterActivity;
import com.aizoban.naitokenzai.views.activities.MangaActivity;
import com.aizoban.naitokenzai.views.adapters.ChapterListingsAdapter;
import com.aizoban.naitokenzai.views.fragments.AddToQueueFragment;
import com.aizoban.naitokenzai.views.fragments.CatalogueFilterFragment;

import java.util.List;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.functions.Func3;
import rx.schedulers.Schedulers;

public class MangaPresenterOnlineImpl implements MangaPresenter {
    public static final String TAG = MangaPresenterOnlineImpl.class.getSimpleName();

    private static final String REQUEST_PARCELABLE_KEY = TAG + ":" + "RequestParcelableKey";

    private static final String INITIALIZED_PARCELABLE_KEY = TAG + ":" + "InitializedParcelableKey";
    private static final String POSITION_PARCELABLE_KEY = TAG + ":" + "PositionParcelableKey";

    private MangaView mMangaView;
    private MangaMapper mMangaMapper;
    private ChapterListingsAdapter mChapterListingsAdapter;

    private RequestWrapper mRequest;
    private Manga mManga;
    private com.aizoban.naitokenzai.models.databases.FavouriteManga mFavouriteManga;

    private boolean mInitialized;
    private Parcelable mPositionSavedState;

    private Subscription mQueryBothMangaAndChaptersSubscription;
    private Subscription mQueryFavouriteMangaSubscription;
    private Subscription mUpdateSubscription;

    public MangaPresenterOnlineImpl(MangaView mangaView, MangaMapper mangaMapper) {
        mMangaView = mangaView;
        mMangaMapper = mangaMapper;
    }

    @Override
    public void handleInitialArguments(Intent arguments) {
        if (arguments != null) {
            if (arguments.hasExtra(MangaActivity.REQUEST_ARGUMENT_KEY)) {
                mRequest = arguments.getParcelableExtra(MangaActivity.REQUEST_ARGUMENT_KEY);

                arguments.removeExtra(MangaActivity.REQUEST_ARGUMENT_KEY);
            }
        }
    }

    @Override
    public void initializeViews() {
        mMangaView.initializeToolbar();
        mMangaView.initializeSwipeRefreshLayout();
        mMangaView.initializeListView();
        mMangaView.initializeEmptyRelativeLayout();
    }

    @Override
    public void initializeDataFromUrl() {
        mChapterListingsAdapter = new ChapterListingsAdapter(mMangaView.getContext());

        mMangaMapper.registerAdapter(mChapterListingsAdapter);

        initializeFavouriteManga();

        if (!mInitialized) {
            updateDataFromUrl();
        }
    }

    @Override
    public void registerForEvents() {
        // Do Nothing.
    }

    @Override
    public void unregisterForEvents() {
        // Do Nothing.
    }

    @Override
    public void onResume() {
        queryBothMangaAndChaptersFromUrl();
    }

    @Override
    public void saveState(Bundle outState) {
        if (mRequest != null) {
            outState.putParcelable(REQUEST_PARCELABLE_KEY, mRequest);
        }

        outState.putBoolean(INITIALIZED_PARCELABLE_KEY, mInitialized);

        if (mMangaMapper.getPositionState() != null) {
            outState.putParcelable(POSITION_PARCELABLE_KEY, mMangaMapper.getPositionState());
        }
    }

    @Override
    public void restoreState(Bundle savedState) {
        if (savedState.containsKey(REQUEST_PARCELABLE_KEY)) {
            mRequest = savedState.getParcelable(REQUEST_PARCELABLE_KEY);

            savedState.remove(REQUEST_PARCELABLE_KEY);
        }
        if (savedState.containsKey(INITIALIZED_PARCELABLE_KEY)) {
            mInitialized = savedState.getBoolean(INITIALIZED_PARCELABLE_KEY, false);

            savedState.remove(INITIALIZED_PARCELABLE_KEY);
        }
        if (savedState.containsKey(POSITION_PARCELABLE_KEY)) {
            mPositionSavedState = savedState.getParcelable(POSITION_PARCELABLE_KEY);

            savedState.remove(POSITION_PARCELABLE_KEY);
        }
    }

    @Override
    public void destroyAllSubscriptions() {
        if (mQueryBothMangaAndChaptersSubscription != null) {
            mQueryBothMangaAndChaptersSubscription.unsubscribe();
            mQueryBothMangaAndChaptersSubscription = null;
        }
        if (mQueryFavouriteMangaSubscription != null) {
            mQueryFavouriteMangaSubscription.unsubscribe();
            mQueryFavouriteMangaSubscription = null;
        }
        if (mUpdateSubscription != null) {
            mUpdateSubscription.unsubscribe();
            mUpdateSubscription = null;
        }
    }

    @Override
    public void releaseAllResources() {
        if (mChapterListingsAdapter != null) {
            mChapterListingsAdapter.setCursor(null);
            mChapterListingsAdapter = null;
        }
    }

    @Override
    public void onApplyColorChange(int color) {
        if (mChapterListingsAdapter != null) {
            mChapterListingsAdapter.setColor(color);
        }
    }

    @Override
    public void onSwipeRefresh() {
        updateDataFromUrl();
    }

    @Override
    public void onChapterClick(int position) {
        if (mChapterListingsAdapter != null) {
            Chapter selectedChapter = (Chapter) mChapterListingsAdapter.getItem(position);
            if (selectedChapter != null) {
                String chapterSource = selectedChapter.getSource();
                String chapterUrl = selectedChapter.getUrl();

                Intent chapterIntent = ChapterActivity.constructOnlineChapterActivityIntent(mMangaView.getContext(), new RequestWrapper(chapterSource, chapterUrl), 0);
                mMangaView.getContext().startActivity(chapterIntent);
            }
        }
    }

    @Override
    public void onFavourite() {
        try {
            if (mManga != null) {
                if (mFavouriteManga != null) {
                    QueryManager.deleteObjectToApplicationDatabase(mFavouriteManga);

                    mFavouriteManga = null;

                    mMangaView.setFavouriteButton(false);
                } else {
                    mFavouriteManga = DefaultFactory.FavouriteManga.constructDefault();
                    mFavouriteManga.setSource(mManga.getSource());
                    mFavouriteManga.setUrl(mManga.getUrl());
                    mFavouriteManga.setName(mManga.getName());
                    mFavouriteManga.setThumbnailUrl(mManga.getThumbnailUrl());

                    QueryManager.putObjectToApplicationDatabase(mFavouriteManga);

                    mMangaView.setFavouriteButton(true);
                }
            }
        } catch (Throwable e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onOptionRefresh() {
        updateDataFromUrl();

        mMangaView.scrollToTop();
    }

    @Override
    public void onOptionDownload() {
        if (((FragmentActivity) mMangaView.getContext()).getSupportFragmentManager().findFragmentByTag(CatalogueFilterFragment.TAG) == null) {
            AddToQueueFragment addToQueueFragment = AddToQueueFragment.newInstance(mRequest);

            addToQueueFragment.show(((FragmentActivity) mMangaView.getContext()).getSupportFragmentManager(), AddToQueueFragment.TAG);
        }
    }

    @Override
    public void onOptionToTop() {
        mMangaView.scrollToTop();
    }

    @Override
    public void onOptionDelete() {
        // Do Nothing.
    }

    @Override
    public void onOptionSelectAll() {
        // Do Nothing.
    }

    @Override
    public void onOptionClear() {
        // Do Nothing.
    }

    private void initializeFavouriteManga() {
        if (mQueryFavouriteMangaSubscription != null) {
            mQueryFavouriteMangaSubscription.unsubscribe();
            mQueryFavouriteMangaSubscription = null;
        }

        if (mRequest != null) {
            mQueryFavouriteMangaSubscription = QueryManager
                    .queryFavouriteMangaFromRequest(mRequest)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Cursor>() {
                        @Override
                        public void onCompleted() {
                            if (mFavouriteManga != null) {
                                mMangaView.initializeFavouriteButton(true);
                            } else {
                                mMangaView.initializeFavouriteButton(false);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onNext(Cursor favouriteCursor) {
                            if (favouriteCursor != null && favouriteCursor.getCount() != 0) {
                                mFavouriteManga = QueryManager.toObject(favouriteCursor, com.aizoban.naitokenzai.models.databases.FavouriteManga.class);
                            }
                        }
                    });
        }
    }

    private void queryBothMangaAndChaptersFromUrl() {
        if (mQueryBothMangaAndChaptersSubscription != null) {
            mQueryBothMangaAndChaptersSubscription.unsubscribe();
            mQueryBothMangaAndChaptersSubscription = null;
        }

        if (mRequest != null) {
            Observable<Cursor> queryMangaFromUrlObservable = QueryManager
                    .queryMangaFromRequest(mRequest);
            Observable<Cursor> queryChaptersFromUrlObservable = QueryManager
                    .queryChaptersOfMangaFromRequest(mRequest);
            Observable<List<String>> queryRecentChapterUrlsObservable = QueryManager
                    .queryRecentChaptersOfMangaFromRequest(mRequest, false)
                    .flatMap(new Func1<Cursor, Observable<RecentChapter>>() {
                        @Override
                        public Observable<RecentChapter> call(Cursor recentChaptersCursor) {
                            List<RecentChapter> recentChapters = QueryManager.toList(recentChaptersCursor, RecentChapter.class);
                            return Observable.from(recentChapters.toArray(new RecentChapter[recentChapters.size()]));
                        }
                    })
                    .flatMap(new Func1<RecentChapter, Observable<String>>() {
                        @Override
                        public Observable<String> call(RecentChapter recentChapter) {
                            return Observable.just(recentChapter.getUrl());
                        }
                    })
                    .toList();

            mQueryBothMangaAndChaptersSubscription = Observable.zip(queryMangaFromUrlObservable, queryChaptersFromUrlObservable, queryRecentChapterUrlsObservable,
                    new Func3<Cursor, Cursor, List<String>, Pair<Pair<Cursor, Cursor>, List<String>>>() {
                        @Override
                        public Pair<Pair<Cursor, Cursor>, List<String>> call(Cursor mangaCursor, Cursor chaptersCursor, List<String> recentChapterUrls) {
                            Pair<Cursor, Cursor> cursorPair = Pair.create(mangaCursor, chaptersCursor);

                            return Pair.create(cursorPair, recentChapterUrls);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Pair<Pair<Cursor, Cursor>, List<String>>>() {
                        @Override
                        public void onCompleted() {
                            if (mManga != null) {
                                if (mManga.isInitialized()) {
                                    mMangaView.setTitle(mManga.getName());
                                    mMangaView.setName(mManga.getName());
                                    mMangaView.setDescription(mManga.getDescription());
                                    mMangaView.setAuthor(mManga.getAuthor());
                                    mMangaView.setArtist(mManga.getArtist());
                                    mMangaView.setGenre(mManga.getGenre());
                                    mMangaView.setIsCompleted(mManga.isCompleted());
                                    mMangaView.setThumbnail(mManga.getThumbnailUrl());

                                    mMangaView.hideEmptyRelativeLayout();
                                    mMangaView.showListViewIfHidden();
                                }
                            }

                            restorePosition();
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onNext(Pair<Pair<Cursor, Cursor>, List<String>> pairListPair) {
                            Pair<Cursor, Cursor> cursorPair = pairListPair.first;
                            List<String> recentChapterUrls = pairListPair.second;

                            if (cursorPair != null) {
                                Cursor mangaCursor = cursorPair.first;
                                if (mangaCursor != null && mangaCursor.getCount() != 0) {
                                    mManga = QueryManager.toObject(mangaCursor, Manga.class);
                                }

                                Cursor chaptersCursor = cursorPair.second;
                                if (chaptersCursor != null && chaptersCursor.getCount() != 0) {
                                    mMangaView.hideChapterStatusError();
                                } else {
                                    mMangaView.showChapterStatusError();
                                }

                                if (mChapterListingsAdapter != null) {
                                    mChapterListingsAdapter.setCursor(chaptersCursor);
                                }
                            }

                            if (recentChapterUrls != null) {
                                if (mChapterListingsAdapter != null) {
                                    mChapterListingsAdapter.setRecentChapterUrls(recentChapterUrls);
                                }
                            }
                        }
                    });
        }
    }

    private void updateDataFromUrl() {
        if (mUpdateSubscription != null) {
            mUpdateSubscription.unsubscribe();
            mUpdateSubscription = null;
        }

        if (mRequest != null) {
            mMangaView.showRefreshing();

            Observable<Manga> updateMangaFromUrl = NaitoKenzaiManager
                    .pullMangaFromNetwork(mRequest);
            Observable<List<Chapter>> updateChaptersFromUrl = NaitoKenzaiManager
                    .pullChaptersFromNetwork(mRequest)
                    .onErrorReturn(new Func1<Throwable, List<Chapter>>() {
                        @Override
                        public List<Chapter> call(Throwable throwable) {
                            // Swallow Error with Empty Chapter List.
                            return null;
                        }
                    });

            mUpdateSubscription = Observable.zip(updateMangaFromUrl, updateChaptersFromUrl,
                    new Func2<Manga, List<Chapter>, Pair<Manga, List<Chapter>>>() {
                        @Override
                        public Pair<Manga, List<Chapter>> call(Manga manga, List<Chapter> chapterList) {
                            return Pair.create(manga, chapterList);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Pair<Manga, List<Chapter>>>() {
                        @Override
                        public void onCompleted() {
                            mMangaView.hideRefreshing();

                            queryBothMangaAndChaptersFromUrl();

                            mInitialized = true;
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }

                            mMangaView.hideRefreshing();
                            mMangaView.toastMangaError();
                        }

                        @Override
                        public void onNext(Pair<Manga, List<Chapter>> mangaListPair) {
                            // Do Nothing.
                        }
                    });
        }
    }

    private void restorePosition() {
        if (mPositionSavedState != null) {
            mMangaMapper.setPositionState(mPositionSavedState);

            mPositionSavedState = null;
        }
    }
}
