package com.example.gkm_bar_fer.licence;

import java.util.Locale;

/**
 * Calculs de dates pour les licences, sans java.time (indisponible avant Android 8).
 * Une date est représentée par un "jour epoch" : nombre de jours depuis le 01/01/1970 (UTC).
 */
public final class LicenseDates {

    /** Jour epoch du 01/01/2020 : origine des dates stockées dans la licence. */
    public static final int JOUR_EPOCH_2020 = 18262;

    private static final long MILLIS_PAR_JOUR = 86_400_000L;

    private LicenseDates() {
        // Classe utilitaire : pas d'instance.
    }

    /** Jour epoch (UTC) correspondant à un instant en millisecondes. */
    public static int jourEpochDepuisMillis(long millis) {
        return (int) Math.floorDiv(millis, MILLIS_PAR_JOUR);
    }

    /** Jour epoch pour une date du calendrier (mois de 1 à 12). */
    public static int jourEpoch(int annee, int mois, int jour) {
        int y = mois <= 2 ? annee - 1 : annee;
        int era = Math.floorDiv(y, 400);
        int yoe = y - era * 400;
        int doy = (153 * (mois + (mois > 2 ? -3 : 9)) + 2) / 5 + jour - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** @return {année, mois (1-12), jour} pour un jour epoch. */
    public static int[] civil(int jourEpoch) {
        int z = jourEpoch + 719468;
        int era = Math.floorDiv(z, 146097);
        int doe = z - era * 146097;
        int yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        int y = yoe + era * 400;
        int doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        int mp = (5 * doy + 2) / 153;
        int d = doy - (153 * mp + 2) / 5 + 1;
        int m = mp < 10 ? mp + 3 : mp - 9;
        return new int[]{m <= 2 ? y + 1 : y, m, d};
    }

    /** Format d'affichage jj/MM/aaaa. */
    public static String formater(int jourEpoch) {
        int[] c = civil(jourEpoch);
        return String.format(Locale.ROOT, "%02d/%02d/%04d", c[2], c[1], c[0]);
    }

    public static boolean estBissextile(int annee) {
        return (annee % 4 == 0 && annee % 100 != 0) || annee % 400 == 0;
    }

    public static int joursDansMois(int annee, int mois) {
        switch (mois) {
            case 2:
                return estBissextile(annee) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * Ajoute des mois à une date en gardant le même jour (ramené à la fin du mois si besoin).
     * Exemple : 18/10/2026 + 12 mois = 18/10/2027 ; 31/01/2026 + 1 mois = 28/02/2026.
     */
    public static int ajouterMois(int jourEpoch, int nbMois) {
        int[] c = civil(jourEpoch);
        int total = c[0] * 12 + (c[1] - 1) + nbMois;
        int annee = Math.floorDiv(total, 12);
        int mois = Math.floorMod(total, 12) + 1;
        int jour = Math.min(c[2], joursDansMois(annee, mois));
        return jourEpoch(annee, mois, jour);
    }
}