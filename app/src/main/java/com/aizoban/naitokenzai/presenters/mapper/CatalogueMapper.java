package com.aizoban.naitokenzai.presenters.mapper;

import android.os.Parcelable;
import android.widget.BaseAdapter;

public interface CatalogueMapper {
    public void registerAdapter(BaseAdapter adapter);

    public Parcelable getPositionState();

    public void setPositionState(Parcelable state);
}
