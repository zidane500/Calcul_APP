package com.example.gkm_bar_fer.licence;

/**
 * Contenu d'une licence (ce qui est signé par la clé privée).
 *
 * Format binaire fixe de 19 octets :
 *   [0]      version du format (1)
 *   [1]      produit (1 = FER_PRO)
 *   [2]      type : 0 = limitée dans le temps, 1 = à vie
 *   [3..14]  Installation ID sans "FP-" ni tirets (12 caractères ASCII)
 *   [15..16] jour de début, en jours depuis le 01/01/2020 (2 octets)
 *   [17..18] jour de fin,   en jours depuis le 01/01/2020 (2 octets, 0 si à vie)
 *
 * Classe 100 % Java (aucune dépendance Android).
 */
public final class LicensePayload {

    public enum Type {
        LIMITEE(0),
        A_VIE(1);

        final int code;

        Type(int code) {
            this.code = code;
        }

        static Type depuisCode(int code) {
            for (Type t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            return null;
        }
    }

    public static final int TAILLE = 19;
    private static final int MAX_DECALAGE = 0xFFFF;

    private final int produitId;
    private final Type type;
    private final String installationId;
    private final int jourDebut;
    private final int jourFin; // ignoré (0) pour une licence à vie

    private LicensePayload(int produitId, Type type, String installationId,
                           int jourDebut, int jourFin) {
        this.produitId = produitId;
        this.type = type;
        this.installationId = installationId;
        this.jourDebut = jourDebut;
        this.jourFin = jourFin;
    }

    /** Licence limitée : valable du jourDebut au jourFin inclus (jours epoch). */
    public static LicensePayload limitee(String installationId, int jourDebut, int jourFin) {
        verifierId(installationId);
        verifierJour(jourDebut);
        verifierJour(jourFin);
        if (jourFin < jourDebut) {
            throw new IllegalArgumentException("La date de fin est avant la date de début.");
        }
        return new LicensePayload(LicenseConstants.PRODUCT_ID, Type.LIMITEE,
                installationId, jourDebut, jourFin);
    }

    /** Licence à vie : valable à partir du jourDebut, sans date de fin. */
    public static LicensePayload aVie(String installationId, int jourDebut) {
        verifierId(installationId);
        verifierJour(jourDebut);
        return new LicensePayload(LicenseConstants.PRODUCT_ID, Type.A_VIE,
                installationId, jourDebut, jourDebut);
    }

    private static void verifierId(String id) {
        if (!InstallationId.estValide(id)) {
            throw new IllegalArgumentException("Installation ID invalide : " + id);
        }
    }

    private static void verifierJour(int jourEpoch) {
        int decalage = jourEpoch - LicenseDates.JOUR_EPOCH_2020;
        if (decalage < 0 || decalage > MAX_DECALAGE) {
            throw new IllegalArgumentException(
                    "Date hors limites (entre 2020 et 2199) : " + LicenseDates.formater(jourEpoch));
        }
    }

    public byte[] toBytes() {
        byte[] b = new byte[TAILLE];
        b[0] = (byte) LicenseConstants.LICENSE_FORMAT_VERSION;
        b[1] = (byte) produitId;
        b[2] = (byte) type.code;
        String corps = installationId.replace("-", "").substring(2); // sans "FP"
        for (int i = 0; i < 12; i++) {
            b[3 + i] = (byte) corps.charAt(i);
        }
        int debut = jourDebut - LicenseDates.JOUR_EPOCH_2020;
        int fin = type == Type.A_VIE ? 0 : jourFin - LicenseDates.JOUR_EPOCH_2020;
        b[15] = (byte) (debut >> 8);
        b[16] = (byte) debut;
        b[17] = (byte) (fin >> 8);
        b[18] = (byte) fin;
        return b;
    }

    /**
     * Relit un contenu de licence.
     *
     * @return la licence, ou null si le format est incorrect (taille, version, type, ID, dates).
     */
    public static LicensePayload fromBytes(byte[] b) {
        if (b == null || b.length != TAILLE) {
            return null;
        }
        if ((b[0] & 0xFF) != LicenseConstants.LICENSE_FORMAT_VERSION) {
            return null;
        }
        Type type = Type.depuisCode(b[2] & 0xFF);
        if (type == null) {
            return null;
        }
        StringBuilder corps = new StringBuilder("FP");
        for (int i = 0; i < 12; i++) {
            corps.append((char) (b[3 + i] & 0xFF));
        }
        String brut = corps.toString();
        String id = brut.substring(0, 2) + "-" + brut.substring(2, 6) + "-"
                + brut.substring(6, 10) + "-" + brut.substring(10, 14);
        if (!InstallationId.estValide(id)) {
            return null;
        }
        int debut = ((b[15] & 0xFF) << 8) | (b[16] & 0xFF);
        int fin = ((b[17] & 0xFF) << 8) | (b[18] & 0xFF);
        int jourDebut = LicenseDates.JOUR_EPOCH_2020 + debut;
        int jourFin = LicenseDates.JOUR_EPOCH_2020 + fin;
        if (type == Type.A_VIE) {
            jourFin = jourDebut;
        } else if (jourFin < jourDebut) {
            return null;
        }
        return new LicensePayload(b[1] & 0xFF, type, id, jourDebut, jourFin);
    }

    public int getProduitId() {
        return produitId;
    }

    public Type getType() {
        return type;
    }

    public String getInstallationId() {
        return installationId;
    }

    public int getJourDebut() {
        return jourDebut;
    }

    /** Dernier jour de validité (inclus). Sans signification pour une licence à vie. */
    public int getJourFin() {
        return jourFin;
    }

    public boolean estAVie() {
        return type == Type.A_VIE;
    }

    /** @return true si la licence est expirée au jour donné. Une licence à vie n'expire jamais. */
    public boolean estExpireeLe(int jourEpoch) {
        return type == Type.LIMITEE && jourEpoch > jourFin;
    }
}