package com.example.gkm_bar_fer;

import android.app.Application;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.gkm_bar_fer.licence.LicenseManager;

public class GkmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // TEMPORAIRE (étape 3) : afficher l'Installation ID dans Logcat pour le tester.
        // Cette ligne sera supprimée à l'étape 6 (quand l'écran d'activation existera).
        Log.d("FerProLicense", "Installation ID = "
                + LicenseManager.getInstance(this).getInstallationId());
    }
}