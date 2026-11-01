package com.example.gkm_bar_fer.licence;

import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Protection contre le retour de date.
 *
 * Principe : on mémorise la date la plus récente jamais vue sur ce téléphone
 * ("dernier instant vu"). Si l'horloge du téléphone revient ensuite en arrière de plus
 * de 24 heures, quelqu'un a modifié la date : l'accès est bloqué (sans rien supprimer).
 *
 * Cette mémoire est signée avec une clé de l'Android Keystore (HMAC-SHA256) : la clé ne
 * peut pas être extraite du téléphone, donc modifier ou copier le fichier de préférences
 * ne permet pas de fabriquer une mémoire valide.
 *
 * Limites assumées : sans serveur, on ne peut pas empêcher une personne qui a les droits
 * "root" de tout réinitialiser. Le but est d'arrêter les contournements simples.
 */
final class ClockGuard {

    /** Signe des données avec une clé qui ne quitte pas le téléphone (Android Keystore). */
    interface Mac {
        byte[] calculer(byte[] donnees) throws GeneralSecurityException;
    }

    enum Verdict {
        /** Date cohérente. */
        OK,
        /** L'horloge est revenue en arrière : date modifiée. */
        DATE_RECULEE,
        /** La mémoire du dernier instant vu a été modifiée ou copiée d'un autre téléphone. */
        ALTERE
    }

    static final String KEY_LAST_SEEN = "last_seen_ms";
    static final String KEY_LAST_SEEN_MAC = "last_seen_mac";

    /** Retour en arrière toléré (petites corrections d'horloge, changements d'heure). */
    static final long TOLERANCE_RECUL_MS = 24L * 60L * 60L * 1000L;

    /** On n'écrit pas plus d'une fois par heure (économie d'écritures et de calculs). */
    static final long PAS_ECRITURE_MS = 60L * 60L * 1000L;

    private final SharedPreferences preferences;
    private final Mac mac; // null = pas de signature disponible

    private boolean chargee = false;
    private boolean referenceConnue = false;
    private long dernierVu;

    ClockGuard(SharedPreferences preferences, Mac mac) {
        this.preferences = preferences;
        this.mac = mac;
    }

    /**
     * Contrôle l'horloge. À appeler quand on a une licence authentique pour ce téléphone.
     *
     * @param maintenantMs   heure actuelle du téléphone (UTC, en millisecondes)
     * @param installationId l'Installation ID (lié à la signature)
     */
    synchronized Verdict controler(long maintenantMs, String installationId) {
        if (!chargee) {
            if (!charger(installationId)) {
                return Verdict.ALTERE;
            }
        }
        if (!referenceConnue) {
            // Première observation : on enregistre la date actuelle comme référence.
            ecrire(maintenantMs, installationId);
            return Verdict.OK;
        }
        if (maintenantMs < dernierVu - TOLERANCE_RECUL_MS) {
            return Verdict.DATE_RECULEE; // on ne touche PAS à la référence
        }
        if (maintenantMs - dernierVu >= PAS_ECRITURE_MS) {
            ecrire(maintenantMs, installationId);
        }
        return Verdict.OK;
    }

    /**
     * Remet la référence à la date actuelle. Réservé aux cas où une NOUVELLE licence
     * (plus récente, délivrée par le vendeur) vient d'être activée : c'est le moyen
     * légitime de repartir à zéro après une date incorrecte.
     */
    synchronized void reinitialiser(long maintenantMs, String installationId) {
        ecrire(maintenantMs, installationId);
    }

    /** @return false si la mémoire a été modifiée (signature incorrecte). */
    private boolean charger(String installationId) {
        String texte = preferences.getString(KEY_LAST_SEEN, null);
        if (texte == null) {
            referenceConnue = false;
            chargee = true;
            return true;
        }
        long valeur;
        try {
            valeur = Long.parseLong(texte);
        } catch (NumberFormatException e) {
            return false;
        }
        String macStocke = preferences.getString(KEY_LAST_SEEN_MAC, null);
        if (mac != null && macStocke != null) {
            String attendu = calculerMac(valeur, installationId);
            // attendu == null : Keystore indisponible, on ne peut pas vérifier (on ne bloque pas).
            if (attendu != null && !egal(attendu, macStocke)) {
                return false;
            }
        }
        dernierVu = valeur;
        referenceConnue = true;
        chargee = true;
        return true;
    }

    private void ecrire(long valeur, String installationId) {
        SharedPreferences.Editor editeur = preferences.edit()
                .putString(KEY_LAST_SEEN, Long.toString(valeur));
        String signature = mac == null ? null : calculerMac(valeur, installationId);
        if (signature != null) {
            editeur.putString(KEY_LAST_SEEN_MAC, signature);
        } else {
            editeur.remove(KEY_LAST_SEEN_MAC);
        }
        editeur.commit();
        dernierVu = valeur;
        referenceConnue = true;
        chargee = true;
    }

    /** @return la signature en hexadécimal, ou null si le Keystore est indisponible. */
    private String calculerMac(long valeur, String installationId) {
        try {
            byte[] donnees = ("FPV1|" + installationId + "|" + valeur)
                    .getBytes(StandardCharsets.UTF_8);
            return hex(mac.calculer(donnees));
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }

    private static boolean egal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] octets) {
        StringBuilder sb = new StringBuilder(octets.length * 2);
        for (byte b : octets) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}