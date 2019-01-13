package com.aizoban.naitokenzai.utils;

import com.aizoban.naitokenzai.controllers.databases.LibraryContract;

public class SearchUtils {
    public static final long TIMEOUT = 500;

    public static final String STATUS_ALL = "ALL";
    public static final String STATUS_COMPLETED = "1";
    public static final String STATUS_ONGOING = "0";

    public static final String ORDER_BY_NAME = LibraryContract.Manga.COLUMN_NAME;
    public static final String ORDER_BY_RANK = LibraryContract.Manga.COLUMN_RANK;
}
