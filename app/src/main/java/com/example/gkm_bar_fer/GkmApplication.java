package com.example.gkm_bar_fer;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.gkm_bar_fer.licence.LicenseGuard;

public class GkmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Licence : contrôle automatique sur TOUS les écrans de l'application.
        registerActivityLifecycleCallbacks(new LicenseGuard());
    }
}