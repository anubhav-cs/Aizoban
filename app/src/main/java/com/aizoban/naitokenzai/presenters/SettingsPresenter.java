package com.aizoban.naitokenzai.presenters;

import android.preference.Preference;

public interface SettingsPresenter {
    public void initializeViews();

    public boolean onPreferenceClick(Preference preference);
}
