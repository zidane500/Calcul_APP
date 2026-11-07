package com.example.gkm_bar_fer.sauvegarde;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Règles de la sauvegarde automatique (délais, nombre de versions, noms de fichiers).
 * Classe 100 % Java, testable sur PC.
 */
public final class SauvegardeRegles {

    private SauvegardeRegles() {
        // Classe utilitaire : pas d'instance.
    }

    /** Attente après le dernier changement, pour regrouper plusieurs modifications en une sauvegarde. */
    public static final long DELAI_REGROUPEMENT_MS = 8_000L;

    /** Écart minimal entre deux fichiers créés (les noms de fichiers sont à la minute). */
    public static final long INTERVALLE_MIN_MS = 60_000L;

    /** Nombre de versions conservées dans le dossier. */
    public static final int NB_VERSIONS_GARDEES = 5;

    /** "FerPro_Sauvegarde_2026-11-06_14h32.ferpro", avec le suffixe " (1)" ajouté par Android en cas de doublon. */
    private static final Pattern NOM_SAUVEGARDE = Pattern.compile(
            "FerPro_Sauvegarde_\\d{4}-\\d{2}-\\d{2}_\\d{2}h\\d{2}( \\(\\d+\\))?\\.ferpro");

    public static boolean estNomSauvegarde(String nom) {
        return nom != null && NOM_SAUVEGARDE.matcher(nom).matches();
    }

    /**
     * Combien de temps attendre avant de sauvegarder.
     * Toujours au moins le délai de regroupement, et jamais moins que l'écart minimal
     * depuis la dernière écriture : la dernière modification est donc toujours sauvegardée.
     */
    public static long delai(long maintenantMs, long derniereEcritureMs) {
        long avantProchaine = derniereEcritureMs + INTERVALLE_MIN_MS - maintenantMs;
        return Math.max(DELAI_REGROUPEMENT_MS, avantProchaine);
    }

    /** Une sauvegarde sans aucun client, calcul ni stock n'est jamais écrite (elle ne protège rien). */
    public static boolean estVide(SauvegardeCodec.Apercu apercu) {
        return apercu.nbClients == 0
                && apercu.nbCalculs == 0
                && apercu.nbTypesStock == 0
                && apercu.nbStocksClients == 0;
    }

    /**
     * @param nomsDuPlusRecentAuPlusAncien noms triés du plus récent au plus ancien
     * @return les noms au-delà des {@code garder} plus récents (à supprimer)
     */
    public static List<String> aSupprimer(List<String> nomsDuPlusRecentAuPlusAncien, int garder) {
        List<String> resultat = new ArrayList<>();
        for (int i = garder; i < nomsDuPlusRecentAuPlusAncien.size(); i++) {
            resultat.add(nomsDuPlusRecentAuPlusAncien.get(i));
        }
        return resultat;
    }
}
