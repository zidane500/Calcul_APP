package com.example.gkm_bar_fer.licence;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Point d'entrée unique du système de licence (même principe que StockManager,
 * ClientManager et CalculManager : singleton obtenu avec getInstance(context)).
 *
 * Séparation stricte : cette classe lit/écrit UNIQUEMENT le fichier "license_prefs".
 * Elle ne touche jamais aux clients, calculs, stock, historique ou exports.
 *
 * ÉTAPE 2 : squelette seulement. Rien n'est encore branché dans l'application.
 */
public class LicenseManager {

    private static LicenseManager instance;

    private final SharedPreferences preferences;

    private LicenseManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(LicenseConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized LicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new LicenseManager(context);
        }
        return instance;
    }

    /**
     * Calcule l'état actuel de la licence.
     * Version provisoire (étape 2) : sera complétée aux étapes 3, 4, 7 et 8.
     */
    public LicenseStatus getStatus() {
        String code = preferences.getString(LicenseConstants.KEY_LICENSE_CODE, null);
        if (code == null || code.trim().isEmpty()) {
            return LicenseStatus.NOT_ACTIVATED;
        }
        // La vérification cryptographique sera ajoutée à l'étape 4.
        return LicenseStatus.INVALID;
    }
}