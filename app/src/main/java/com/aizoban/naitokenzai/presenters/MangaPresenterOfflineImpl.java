package com.aizoban.naitokenzai.presenters;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;
import android.util.Pair;
import android.util.SparseBooleanArray;

import com.aizoban.naitokenzai.BuildConfig;
import com.aizoban.naitokenzai.controllers.QueryManager;
import com.aizoban.naitokenzai.controllers.databases.DatabaseService;
import com.aizoban.naitokenzai.controllers.events.DownloadChapterDeleteEvent;
import com.aizoban.naitokenzai.controllers.factories.DefaultFactory;
import com.aizoban.naitokenzai.models.Chapter;
import com.aizoban.naitokenzai.models.databases.RecentChapter;
import com.aizoban.naitokenzai.models.downloads.DownloadChapter;
import com.aizoban.naitokenzai.models.downloads.DownloadManga;
import com.aizoban.naitokenzai.presenters.mapper.MangaMapper;
import com.aizoban.naitokenzai.utils.wrappers.DownloadChapterSortCursorWrapper;
import com.aizoban.naitokenzai.utils.wrappers.RequestWrapper;
import com.aizoban.naitokenzai.views.MangaView;
import com.aizoban.naitokenzai.views.activities.ChapterActivity;
import com.aizoban.naitokenzai.views.activities.MangaActivity;
import com.aizoban.naitokenzai.views.adapters.DownloadChapterListingsAdapter;
import com.aizoban.naitokenzai.views.fragments.AddToQueueFragment;
import com.aizoban.naitokenzai.views.fragments.CatalogueFilterFragment;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.functions.Func3;
import rx.schedulers.Schedulers;

public class MangaPresenterOfflineImpl implements MangaPresenter {
    public static final String TAG = MangaPresenterOfflineImpl.class.getSimpleName();

    private static final String REQUEST_PARCELABLE_KEY = TAG + ":" + "RequestParcelableKey";

    private static final String POSITION_PARCELABLE_KEY = TAG + ":" + "PositionParcelableKey";

    private MangaView mMangaView;
    private MangaMapper mMangaMapper;
    private DownloadChapterListingsAdapter mDownloadChapterListingsAdapter;

    private RequestWrapper mRequest;
    private DownloadManga mDownloadManga;
    private com.aizoban.naitokenzai.models.databases.FavouriteManga mFavouriteManga;

    private Parcelable mPositionSavedState;

    private Subscription mQueryBothMangaAndChaptersSubscription;
    private Subscription mQueryFavouriteMangaSubscription;
    private Subscription mQueryDeleteMangaSubscription;

    public MangaPresenterOfflineImpl(MangaView mangaView, MangaMapper mangaMapper) {
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
        mMangaView.initializeDeletionListView();
        mMangaView.initializeEmptyRelativeLayout();
    }

    @Override
    public void initializeDataFromUrl() {
        mDownloadChapterListingsAdapter = new DownloadChapterListingsAdapter(mMangaView.getContext());

        mMangaMapper.registerAdapter(mDownloadChapterListingsAdapter);

        initializeFavouriteManga();

        queryBothMangaAndChaptersFromUrl();
    }

    @Override
    public void registerForEvents() {
        EventBus.getDefault().register(this);
    }

    public void onEventMainThread(DownloadChapterDeleteEvent event) {
        if (event != null) {
            deleteDownloadMangaIfNoDownloadChapters();

            queryBothMangaAndChaptersFromUrl();
        }
    }

    @Override
    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onResume() {
        // Do Nothing.
    }

    @Override
    public void saveState(Bundle outState) {
        if (mRequest != null) {
            outState.putParcelable(REQUEST_PARCELABLE_KEY, mRequest);
        }
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
        if (mQueryDeleteMangaSubscription != null) {
            mQueryDeleteMangaSubscription.unsubscribe();
            mQueryDeleteMangaSubscription = null;
        }
    }

    @Override
    public void releaseAllResources() {
        if (mDownloadChapterListingsAdapter != null) {
            mDownloadChapterListingsAdapter.setCursor(null);
            mDownloadChapterListingsAdapter = null;
        }
    }

    @Override
    public void onApplyColorChange(int color) {
        if (mDownloadChapterListingsAdapter != null) {
            mDownloadChapterListingsAdapter.setColor(color);
        }
    }

    @Override
    public void onSwipeRefresh() {
        queryBothMangaAndChaptersFromUrl();
    }

    @Override
    public void onChapterClick(int position) {
        if (mDownloadChapterListingsAdapter != null) {
            DownloadChapter selectedDownloadChapter = (DownloadChapter) mDownloadChapterListingsAdapter.getItem(position);
            if (selectedDownloadChapter != null) {
                String chapterSource = selectedDownloadChapter.getSource();
                String chapterUrl = selectedDownloadChapter.getUrl();

                Intent downloadChapterIntent = ChapterActivity.constructOfflineChapterActivityIntent(mMangaView.getContext(), new RequestWrapper(chapterSource, chapterUrl), 0);
                mMangaView.getContext().startActivity(downloadChapterIntent);
            }
        }
    }

    @Override
    public void onFavourite() {
        try {
            if (mDownloadManga != null) {
                if (mFavouriteManga != null) {
                    QueryManager.deleteObjectToApplicationDatabase(mFavouriteManga);

                    mFavouriteManga = null;

                    mMangaView.setFavouriteButton(false);
                } else {
                    mFavouriteManga = DefaultFactory.FavouriteManga.constructDefault();
                    mFavouriteManga.setSource(mDownloadManga.getSource());
                    mFavouriteManga.setUrl(mDownloadManga.getUrl());
                    mFavouriteManga.setName(mDownloadManga.getName());
                    mFavouriteManga.setThumbnailUrl(mDownloadManga.getThumbnailUrl());

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
        queryBothMangaAndChaptersFromUrl();

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
        if (mDownloadChapterListingsAdapter != null) {
            ArrayList<DownloadChapter> chaptersToDelete = new ArrayList<DownloadChapter>();

            SparseBooleanArray checkedItems = mMangaMapper.getCheckedItemPositions();
            for (int index = 0; index < mDownloadChapterListingsAdapter.getCount(); index++) {
                if (checkedItems.get(index + mMangaView.getHeaderViewsCount())) {
                    DownloadChapter downloadChapter = (DownloadChapter) mDownloadChapterListingsAdapter.getItem(index);
                    if (downloadChapter != null) {
                        chaptersToDelete.add(downloadChapter);
                    }
                }
            }

            Intent startService = new Intent(mMangaView.getContext(), DatabaseService.class);
            startService.putExtra(DatabaseService.INTENT_DELETE_DOWNLOAD_CHAPTERS, chaptersToDelete);
            mMangaView.getContext().startService(startService);
        }
    }

    @Override
    public void onOptionSelectAll() {
        mMangaView.selectAll();
    }

    @Override
    public void onOptionClear() {
        mMangaView.clear();
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
            mMangaView.showRefreshing();

            Observable<Cursor> queryDownloadChaptersFromUrlObservable = QueryManager
                    .queryDownloadChaptersOfDownloadManga(mRequest, true);
            Observable<List<String>> queryChapterUrlsFromUrlObservable = QueryManager
                    .queryChaptersOfMangaFromRequest(mRequest)
                    .flatMap(new Func1<Cursor, Observable<Chapter>>() {
                        @Override
                        public Observable<Chapter> call(Cursor chapterCursor) {
                            List<Chapter> chapters = QueryManager.toList(chapterCursor, Chapter.class);
                            return Observable.from(chapters.toArray(new Chapter[chapters.size()]));
                        }
                    })
                    .flatMap(new Func1<Chapter, Observable<String>>() {
                        @Override
                        public Observable<String> call(Chapter chapter) {
                            return Observable.just(chapter.getUrl());
                        }
                    })
                    .toList();

            Observable<Cursor> queryMangaFromUrlObservable = QueryManager
                    .queryDownloadMangaFromRequest(mRequest);
            Observable<Cursor> querySortedDownloadChaptersObservable = Observable.zip(queryDownloadChaptersFromUrlObservable, queryChapterUrlsFromUrlObservable,
                    new Func2<Cursor, List<String>, Cursor>() {
                        @Override
                        public Cursor call(Cursor downloadChapterCursor, List<String> sortedChapterUrls) {
                            return new DownloadChapterSortCursorWrapper(downloadChapterCursor, sortedChapterUrls);
                        }
                    });
            Observable<List<String>> queryRecentChapterUrlsObservable = QueryManager
                    .queryRecentChaptersOfMangaFromRequest(mRequest, true)
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

            mQueryBothMangaAndChaptersSubscription = Observable.zip(queryMangaFromUrlObservable, querySortedDownloadChaptersObservable, queryRecentChapterUrlsObservable,
                    new Func3<Cursor, Cursor, List<String>, Pair<Pair<Cursor, Cursor>, List<String>>>() {
                        @Override
                        public Pair<Pair<Cursor, Cursor>, List<String>> call(Cursor downloadMangaCursor, Cursor downloadChapterCursor, List<String> recentChapterUrls) {
                            Pair<Cursor, Cursor> cursorPair = Pair.create(downloadMangaCursor, downloadChapterCursor);

                            return Pair.create(cursorPair, recentChapterUrls);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Pair<Pair<Cursor, Cursor>, List<String>>>() {
                        @Override
                        public void onCompleted() {
                            if (mDownloadManga != null) {
                                mMangaView.setTitle(mDownloadManga.getName());
                                mMangaView.setName(mDownloadManga.getName());
                                mMangaView.setDescription(mDownloadManga.getDescription());
                                mMangaView.setAuthor(mDownloadManga.getAuthor());
                                mMangaView.setArtist(mDownloadManga.getArtist());
                                mMangaView.setGenre(mDownloadManga.getGenre());
                                mMangaView.setIsCompleted(mDownloadManga.isCompleted());
                                mMangaView.setThumbnail(mDownloadManga.getThumbnailUrl());

                                mMangaView.hideEmptyRelativeLayout();
                                mMangaView.showListViewIfHidden();
                            }

                            restorePosition();

                            mMangaView.hideRefreshing();
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }

                            mMangaView.hideRefreshing();
                        }

                        @Override
                        public void onNext(Pair<Pair<Cursor, Cursor>, List<String>> pairListPair) {
                            Pair<Cursor, Cursor> cursorPair = pairListPair.first;
                            List<String> recentChapterUrls = pairListPair.second;

                            if (cursorPair != null) {
                                Cursor downloadMangaCursor = cursorPair.first;
                                if (downloadMangaCursor != null && downloadMangaCursor.getCount() != 0) {
                                    mDownloadManga = QueryManager.toObject(downloadMangaCursor, DownloadManga.class);
                                }

                                Cursor downloadChaptersCursor = cursorPair.second;
                                if (downloadChaptersCursor != null && downloadChaptersCursor.getCount() != 0) {
                                    mMangaView.hideChapterStatusError();
                                } else {
                                    mMangaView.showChapterStatusError();
                                }

                                if (mDownloadChapterListingsAdapter != null) {
                                    mDownloadChapterListingsAdapter.setCursor(downloadChaptersCursor);
                                }
                            }

                            if (recentChapterUrls != null) {
                                if (mDownloadChapterListingsAdapter != null) {
                                    mDownloadChapterListingsAdapter.setRecentChapterUrls(recentChapterUrls);
                                }
                            }
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

    private void deleteDownloadMangaIfNoDownloadChapters() {
        if (mDownloadManga != null) {
            Intent startService = new Intent(mMangaView.getContext(), DatabaseService.class);
            startService.putExtra(DatabaseService.INTENT_DELETE_DOWNLOAD_MANGA, mDownloadManga);
            mMangaView.getContext().startService(startService);
        }
    }
}
