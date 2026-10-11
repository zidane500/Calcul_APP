package com.example.gkm_bar_fer;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class GkmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}