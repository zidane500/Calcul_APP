package com.example.gkm_bar_fer.licence;

/**
 * Constantes du système de licence Fer Pro.
 * Aucune clé privée ici : uniquement la clé PUBLIQUE (ajoutée à l'étape 4).
 */
public final class LicenseConstants {

    private LicenseConstants() {
        // Classe utilitaire : pas d'instance.
    }

    /** Identifiant du produit, doit être identique dans le générateur PC. */
    public static final String PRODUCT_CODE = "FER_PRO";

    /** Version du format de licence (permet d'évoluer plus tard sans casser les anciennes). */
    public static final int LICENSE_FORMAT_VERSION = 1;

    /** Fichier SharedPreferences dédié à la licence (séparé des données métier). */
    public static final String PREFS_NAME = "license_prefs";

    public static final String KEY_INSTALLATION_ID = "installation_id";
    public static final String KEY_LICENSE_CODE = "license_code";

    /** Téléphone affiché sur l'écran de renouvellement. À remplacer par ton vrai numéro. */
    public static final String SUPPORT_PHONE = "XX XXX XXX";

    /**
     * Clé publique ECDSA P-256 (X.509, encodée en Base64).
     * Sera remplie à l'ÉTAPE 4 avec la clé générée sur ton PC.
     */
    public static final String PUBLIC_KEY_BASE64 = "";
}