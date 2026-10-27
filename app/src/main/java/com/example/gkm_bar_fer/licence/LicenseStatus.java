package com.example.gkm_bar_fer.licence;

/**
 * État de la licence de l'application Fer Pro.
 * Cet état est calculé par LicenseManager et ne dépend JAMAIS des données métier.
 */
public enum LicenseStatus {

    /** Aucune licence enregistrée : l'écran d'activation doit s'afficher. */
    NOT_ACTIVATED,

    /** Licence valide : l'application est utilisable normalement. */
    VALID,

    /** Licence authentique mais expirée : l'écran de renouvellement doit s'afficher. */
    EXPIRED,

    /** Licence illisible, falsifiée ou appartenant à un autre appareil : accès refusé. */
    INVALID,

    /** La date du téléphone est incohérente (retour en arrière détecté) : accès bloqué. */
    CLOCK_ERROR;

    /** @return true uniquement si l'utilisateur peut utiliser l'application. */
    public boolean autoriseAcces() {
        return this == VALID;
    }
}