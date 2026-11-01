package com.example.gkm_bar_fer.licence;

import android.content.Context;

/**
 * Version DEBUG (jamais incluse dans l'APK de production) : permet de simuler une date
 * pour tester l'expiration sans modifier l'heure du téléphone.
 * Le décalage (en jours) est écrit par DebugClockReceiver, via une commande adb.
 */
final class DebugClock {

    private static final String PREFS = "debug_clock";
    private static final String KEY_DECALAGE_JOURS = "decalage_jours";
    private static final long MS_PAR_JOUR = 86_400_000L;

    private DebugClock() {
    }

    static long maintenantMs(Context context) {
        int jours = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_DECALAGE_JOURS, 0);
        return System.currentTimeMillis() + jours * MS_PAR_JOUR;
    }

    static void definirDecalageJours(Context context, int jours) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_DECALAGE_JOURS, jours).commit();
    }
}