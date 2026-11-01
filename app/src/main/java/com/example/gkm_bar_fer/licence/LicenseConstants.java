package com.example.gkm_bar_fer.licence;

/**
 * Constantes du système de licence Fer Pro.
 * Aucune clé privée ici : uniquement la clé PUBLIQUE (ajoutée à l'étape 5).
 */
public final class LicenseConstants {

    private LicenseConstants() {
        // Classe utilitaire : pas d'instance.
    }

    /** Nom lisible du produit (documentation). */
    public static final String PRODUCT_CODE = "FER_PRO";

    /** Identifiant du produit écrit DANS la licence signée (1 = FER_PRO). */
    public static final int PRODUCT_ID = 1;

    /** Version du format de licence (permet d'évoluer plus tard sans casser les anciennes). */
    public static final int LICENSE_FORMAT_VERSION = 1;

    /** Fichier SharedPreferences dédié à la licence (séparé des données métier). */
    public static final String PREFS_NAME = "license_prefs";

    public static final String KEY_INSTALLATION_ID = "installation_id";
    public static final String KEY_LICENSE_CODE = "license_code";

    /** Téléphone affiché sur l'écran de renouvellement. À remplacer par ton vrai numéro. */
    public static final String SUPPORT_PHONE = "52 827 067";

    /**
     * Tolérance (en jours) si la date de début d'une licence est dans le futur :
     * évite un refus à cause d'un décalage d'heure ou de fuseau horaire.
     */
    public static final int TOLERANCE_DEBUT_JOURS = 1;

    /** Nombre de caractères par groupe quand un code est affiché (lisibilité). */
    public static final int TAILLE_GROUPE_CODE = 6;

    /**
     * Clé publique ECDSA P-256 (format X.509) encodée en Base32.
     * Elle sera remplie à l'ÉTAPE 5 avec la clé générée sur ton PC.
     * (C'est la clé PUBLIQUE : elle peut être dans l'APK sans danger.)
     */
    public static final String PUBLIC_KEY_BASE32 = "GBMTAEYGA4VIMSGOHUBACBQIFKDERTR5AMAQOA2CAACCMNNLM3H4VQ2B2PDFDD6G5SDEKTQOHC7IFQ2UU6PFGRE7HAP57RBZAMUAKDM3645XMTINGMGHL4FFD5KW2G74UIKX2Z27MSROMELTEE";
}