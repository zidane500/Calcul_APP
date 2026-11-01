package com.example.gkm_bar_fer.licence;

import android.content.Context;

/**
 * Version RELEASE : l'heure est toujours l'heure réelle du téléphone.
 * Aucun mécanisme de simulation n'existe dans l'APK distribué aux clients.
 */
final class DebugClock {

    private DebugClock() {
    }

    static long maintenantMs(Context context) {
        return System.currentTimeMillis();
    }
}