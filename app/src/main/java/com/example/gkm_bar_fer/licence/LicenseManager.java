package com.example.gkm_bar_fer.licence;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Point d'entrée unique du système de licence (même principe que StockManager,
 * ClientManager et CalculManager : singleton obtenu avec getInstance(context)).
 *
 * Séparation stricte : cette classe lit/écrit UNIQUEMENT le fichier "license_prefs".
 * Elle ne touche jamais aux clients, calculs, stock, historique ou exports.
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
     * Retourne l'Installation ID de ce téléphone.
     * Il est créé au premier appel, puis conservé (même après redémarrage ou
     * expiration de la licence). Il n'est PAS sauvegardé dans le cloud Google.
     */
    public synchronized String getInstallationId() {
        String id = preferences.getString(LicenseConstants.KEY_INSTALLATION_ID, null);
        if (InstallationId.estValide(id)) {
            return id;
        }
        String nouveau = InstallationId.generer();
        // commit() (et non apply()) : on veut être sûr que l'ID est écrit sur le disque
        // avant de l'afficher au client.
        preferences.edit()
                .putString(LicenseConstants.KEY_INSTALLATION_ID, nouveau)
                .commit();
        return nouveau;
    }

    /**
     * Calcule l'état actuel de la licence.
     * Version provisoire : sera complétée aux étapes 4, 7 et 8.
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