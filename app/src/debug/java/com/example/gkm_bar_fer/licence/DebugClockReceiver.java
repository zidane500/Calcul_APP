package com.example.gkm_bar_fer.licence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * Récepteur de TEST (version debug uniquement) : reçoit une commande adb et décale la date
 * utilisée par la licence. Exemple : jours = 3 simule "dans 3 jours", 0 revient à la réalité.
 */
public class DebugClockReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.example.gkm_bar_fer.DEBUG_CLOCK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }
        int jours = intent.getIntExtra("jours", 0);
        DebugClock.definirDecalageJours(context, jours);

        String message = jours == 0
                ? "Date simulée : aucune (date réelle)"
                : "Date simulée : " + LicenseDates.formater(
                LicenseDates.jourEpochDepuisMillis(DebugClock.maintenantMs(context)))
                  + " (" + (jours > 0 ? "+" : "") + jours + " jours)";
        Log.i("FerProDebug", message);
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}