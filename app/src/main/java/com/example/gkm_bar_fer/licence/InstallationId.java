package com.example.gkm_bar_fer.licence;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Installation ID de Fer Pro, au format FP-XXXX-XXXX-XXXX.
 *
 * - Généré aléatoirement (SecureRandom) : aucune donnée personnelle, aucun IMEI.
 * - Alphabet sans caractères ambigus (pas de 0, O, 1, I, L) pour éviter les erreurs
 *   quand le client dicte ou recopie l'identifiant.
 * - Classe 100 % Java (aucune dépendance Android) : elle peut être testée sur PC.
 */
public final class InstallationId {

    private static final String PREFIXE = "FP";
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // 31 caractères
    private static final int NB_GROUPES = 3;
    private static final int TAILLE_GROUPE = 4;

    private static final Pattern FORMAT =
            Pattern.compile("^FP-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$");

    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private InstallationId() {
        // Classe utilitaire : pas d'instance.
    }

    /** Génère un nouvel Installation ID, par exemple FP-A7K2-98MX-42PQ. */
    public static String generer() {
        StringBuilder sb = new StringBuilder(PREFIXE);
        for (int g = 0; g < NB_GROUPES; g++) {
            sb.append('-');
            for (int i = 0; i < TAILLE_GROUPE; i++) {
                sb.append(ALPHABET.charAt(ALEATOIRE.nextInt(ALPHABET.length())));
            }
        }
        return sb.toString();
    }

    /** @return true si la chaîne respecte exactement le format FP-XXXX-XXXX-XXXX. */
    public static boolean estValide(String id) {
        return id != null && FORMAT.matcher(id).matches();
    }

    /**
     * Nettoie une saisie : majuscules, espaces et tirets facultatifs.
     * "fp a7k2 98mx 42pq" devient "FP-A7K2-98MX-42PQ".
     *
     * @return l'ID normalisé, ou null si la saisie n'est pas un Installation ID valide.
     */
    public static String normaliser(String saisie) {
        if (saisie == null) {
            return null;
        }
        String brut = saisie.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (!brut.startsWith(PREFIXE)
                || brut.length() != PREFIXE.length() + NB_GROUPES * TAILLE_GROUPE) {
            return null;
        }
        StringBuilder sb = new StringBuilder(PREFIXE);
        for (int g = 0; g < NB_GROUPES; g++) {
            int debut = PREFIXE.length() + g * TAILLE_GROUPE;
            sb.append('-').append(brut, debut, debut + TAILLE_GROUPE);
        }
        String resultat = sb.toString();
        return estValide(resultat) ? resultat : null;
    }
}