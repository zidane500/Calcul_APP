package com.example.gkm_bar_fer.licence;

/**
 * Contenu d'une licence (ce qui est signé par la clé privée).
 *
 * Deux formats binaires coexistent (le 1er octet indique lequel) :
 *
 * VERSION 1 (19 octets) : anciens codes, déjà vendus. Donne implicitement le droit « stock ».
 *   [0]      version du format (1)
 *   [1]      produit (1 = FER_PRO)
 *   [2]      type : 0 = limitée dans le temps, 1 = à vie
 *   [3..14]  Installation ID sans "FP-" ni tirets (12 caractères ASCII)
 *   [15..16] jour de début, en jours depuis le 01/01/2020 (2 octets)
 *   [17..18] jour de fin,   en jours depuis le 01/01/2020 (2 octets, 0 si à vie)
 *
 * VERSION 2 (20 octets) : mêmes 19 octets (avec la version = 2) + 1 octet de droits.
 *   [19]     droits : bit 0 (valeur 1) = utiliser les fonctionnalités du stock.
 *            Les bits inconnus sont ignorés (compatibilité avec de futurs droits).
 *
 * Règle d'émission (générateur) : si le droit stock est accordé, on émet une licence
 * version 1 (octets IDENTIQUES à avant : elle reste lisible par les anciennes versions
 * de l'application). Si le droit stock est refusé, on émet une version 2.
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

    /** Taille du contenu en version 1 (inchangée : sert aussi aux anciens tests). */
    public static final int TAILLE = 19;

    /** Taille du contenu en version 2 (version 1 + 1 octet de droits). */
    public static final int TAILLE_V2 = 20;

    /** Droit : utiliser les fonctionnalités du stock (stock déchets et stock client). */
    public static final int DROIT_STOCK = 1;

    /** Tous les droits connus de cette version de l'application. */
    private static final int DROITS_CONNUS = DROIT_STOCK;

    /** Droits d'une licence version 1 (avant l'existence des droits) : le stock était inclus. */
    private static final int DROITS_VERSION_1 = DROIT_STOCK;

    private static final int MAX_DECALAGE = 0xFFFF;

    private final int produitId;
    private final Type type;
    private final String installationId;
    private final int jourDebut;
    private final int jourFin; // ignoré (0) pour une licence à vie
    private final int droits;  // combinaison de DROIT_xxx

    private LicensePayload(int produitId, Type type, String installationId,
                           int jourDebut, int jourFin, int droits) {
        this.produitId = produitId;
        this.type = type;
        this.installationId = installationId;
        this.jourDebut = jourDebut;
        this.jourFin = jourFin;
        this.droits = droits;
    }

    /** Licence limitée avec tous les droits (stock inclus) : valable du jourDebut au jourFin inclus. */
    public static LicensePayload limitee(String installationId, int jourDebut, int jourFin) {
        return limitee(installationId, jourDebut, jourFin, true);
    }

    /**
     * Licence limitée : valable du jourDebut au jourFin inclus (jours epoch).
     *
     * @param droitStock true = les fonctionnalités du stock sont autorisées
     */
    public static LicensePayload limitee(String installationId, int jourDebut, int jourFin,
                                         boolean droitStock) {
        verifierId(installationId);
        verifierJour(jourDebut);
        verifierJour(jourFin);
        if (jourFin < jourDebut) {
            throw new IllegalArgumentException("La date de fin est avant la date de début.");
        }
        return new LicensePayload(LicenseConstants.PRODUCT_ID, Type.LIMITEE,
                installationId, jourDebut, jourFin, droitsDepuis(droitStock));
    }

    /** Licence à vie avec tous les droits (stock inclus) : valable à partir du jourDebut. */
    public static LicensePayload aVie(String installationId, int jourDebut) {
        return aVie(installationId, jourDebut, true);
    }

    /**
     * Licence à vie : valable à partir du jourDebut, sans date de fin.
     *
     * @param droitStock true = les fonctionnalités du stock sont autorisées
     */
    public static LicensePayload aVie(String installationId, int jourDebut, boolean droitStock) {
        verifierId(installationId);
        verifierJour(jourDebut);
        return new LicensePayload(LicenseConstants.PRODUCT_ID, Type.A_VIE,
                installationId, jourDebut, jourDebut, droitsDepuis(droitStock));
    }

    private static int droitsDepuis(boolean droitStock) {
        return droitStock ? DROIT_STOCK : 0;
    }

    /**
     * Taille du contenu signé pour une version de format donnée.
     *
     * @return 19 (version 1), 20 (version 2), ou -1 si la version est inconnue
     */
    public static int tailleContenuPourVersion(int version) {
        if (version == LicenseConstants.LICENSE_FORMAT_VERSION) {
            return TAILLE;
        }
        if (version == LicenseConstants.LICENSE_FORMAT_VERSION_2) {
            return TAILLE_V2;
        }
        return -1;
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

    /**
     * Octets à signer. Droits = exactement ceux d'une licence version 1 (stock inclus) :
     * on produit une version 1 (19 octets, identique aux anciens codes). Sinon : version 2.
     */
    public byte[] toBytes() {
        boolean version1 = droits == DROITS_VERSION_1;
        byte[] b = new byte[version1 ? TAILLE : TAILLE_V2];
        b[0] = (byte) (version1 ? LicenseConstants.LICENSE_FORMAT_VERSION
                : LicenseConstants.LICENSE_FORMAT_VERSION_2);
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
        if (!version1) {
            b[19] = (byte) droits;
        }
        return b;
    }

    /**
     * Relit un contenu de licence.
     *
     * @return la licence, ou null si le format est incorrect (taille, version, type, ID, dates).
     */
    public static LicensePayload fromBytes(byte[] b) {
        if (b == null || b.length == 0) {
            return null;
        }
        int version = b[0] & 0xFF;
        int tailleAttendue = tailleContenuPourVersion(version);
        if (tailleAttendue < 0 || b.length != tailleAttendue) {
            return null;
        }
        // Version 1 : droits d'avant (stock inclus). Version 2 : octet de droits (bits inconnus ignorés).
        int droits = version == LicenseConstants.LICENSE_FORMAT_VERSION
                ? DROITS_VERSION_1
                : (b[19] & 0xFF) & DROITS_CONNUS;
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
        return new LicensePayload(b[1] & 0xFF, type, id, jourDebut, jourFin, droits);
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

    /** @return true si cette licence autorise les fonctionnalités du stock. */
    public boolean donneDroitStock() {
        return (droits & DROIT_STOCK) != 0;
    }

    public boolean estAVie() {
        return type == Type.A_VIE;
    }

    /** @return true si la licence est expirée au jour donné. Une licence à vie n'expire jamais. */
    public boolean estExpireeLe(int jourEpoch) {
        return type == Type.LIMITEE && jourEpoch > jourFin;
    }
}