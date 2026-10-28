package ferpro.generator;

import com.example.gkm_bar_fer.licence.Base32;
import com.example.gkm_bar_fer.licence.InstallationId;
import com.example.gkm_bar_fer.licence.LicenseConstants;
import com.example.gkm_bar_fer.licence.LicenseDates;
import com.example.gkm_bar_fer.licence.LicensePayload;
import com.example.gkm_bar_fer.licence.LicenseVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Locale;

/**
 * Logique du générateur (sans interface graphique) : saisie -> licence signée.
 * Le format de la licence vient DIRECTEMENT des classes de l'application Android
 * (LicensePayload, Base32...) : une seule source de vérité, aucun risque de divergence.
 */
public final class LicenseService {

    /** Durées proposées dans le générateur. */
    public enum Duree {
        UN_JOUR_TEST("1 jour (test)", 0, 1),
        UN_MOIS("1 mois", 1, 0),
        TROIS_MOIS("3 mois", 3, 0),
        SIX_MOIS("6 mois", 6, 0),
        UN_AN("1 an", 12, 0),
        DEUX_ANS("2 ans", 24, 0),
        LIFETIME("Lifetime", 0, 0);

        private final String libelle;
        private final int mois;
        private final int jours;

        Duree(String libelle, int mois, int jours) {
            this.libelle = libelle;
            this.mois = mois;
            this.jours = jours;
        }

        public int getMois() {
            return mois;
        }

        public int getJours() {
            return jours;
        }

        public boolean estAVie() {
            return this == LIFETIME;
        }

        @Override
        public String toString() {
            return libelle;
        }
    }

    /** Licence générée : le code à envoyer au client + son contenu. */
    public static final class Resultat {
        public final String code;
        public final LicensePayload licence;

        Resultat(String code, LicensePayload licence) {
            this.code = code;
            this.licence = licence;
        }
    }

    private LicenseService() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Date d'expiration automatique : début + durée (ex. 18/10/2026 + 1 an = 18/10/2027).
     * Pour "1 jour (test)" : début + 1 jour (valable aujourd'hui et demain).
     */
    public static int calculerFin(int jourDebut, Duree duree) {
        if (duree.getJours() > 0) {
            return jourDebut + duree.getJours();
        }
        return LicenseDates.ajouterMois(jourDebut, duree.getMois());
    }

    /**
     * Lit une date jj/MM/aaaa.
     *
     * @throws IllegalArgumentException avec un message clair si la date est incorrecte.
     */
    public static int lireDate(String texte) {
        String[] p = texte == null ? new String[0] : texte.trim().split("/");
        if (p.length != 3) {
            throw new IllegalArgumentException("Date invalide : \"" + texte + "\". Format attendu : jj/mm/aaaa.");
        }
        try {
            int jour = Integer.parseInt(p[0].trim());
            int mois = Integer.parseInt(p[1].trim());
            int annee = Integer.parseInt(p[2].trim());
            if (mois < 1 || mois > 12 || annee < 2020 || annee > 2199
                    || jour < 1 || jour > LicenseDates.joursDansMois(annee, mois)) {
                throw new NumberFormatException();
            }
            return LicenseDates.jourEpoch(annee, mois, jour);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Date invalide : \"" + texte
                    + "\" (jour/mois/année entre 2020 et 2199).");
        }
    }

    /**
     * Génère et signe une licence, puis la RE-VÉRIFIE avec la clé publique
     * (exactement comme le fera l'application) avant de la rendre.
     *
     * @param jourFin ignoré pour Lifetime
     */
    public static Resultat generer(PrivateKey clePrivee, PublicKey clePublique,
                                   String installationIdSaisi, Duree duree,
                                   int jourDebut, int jourFin) {
        String id = InstallationId.normaliser(installationIdSaisi);
        if (id == null) {
            throw new IllegalArgumentException("Installation ID invalide.\n"
                    + "Format attendu : FP-XXXX-XXXX-XXXX (les caractères 0, O, 1, I, L n'existent pas).");
        }
        LicensePayload licence = duree.estAVie()
                ? LicensePayload.aVie(id, jourDebut)
                : LicensePayload.limitee(id, jourDebut, jourFin);

        try {
            byte[] contenu = licence.toBytes();
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(clePrivee);
            signature.update(contenu);
            byte[] sig = signature.sign();

            byte[] tout = new byte[contenu.length + sig.length];
            System.arraycopy(contenu, 0, tout, 0, contenu.length);
            System.arraycopy(sig, 0, tout, contenu.length, sig.length);
            String code = Base32.grouper(Base32.encode(tout), LicenseConstants.TAILLE_GROUPE_CODE);

            // Contrôle final : la licence produite doit être acceptée par le vérificateur.
            LicenseVerifier.Verification v =
                    new LicenseVerifier(clePublique).verifier(code, id, jourDebut);
            if (!v.estOk()) {
                throw new IllegalStateException("Auto-vérification échouée : " + v.getResultat());
            }
            return new Resultat(code, licence);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erreur de signature : " + e.getMessage(), e);
        }
    }

    /**
     * Ajoute une ligne au journal CSV (ouvrable dans Excel, séparateur « ; »).
     * Le journal ne contient PAS de clé : seulement client, ID, type et dates.
     */
    public static void ajouterAuJournal(Path journal, String client, LicensePayload licence,
                                        int jourGeneration) throws IOException {
        boolean nouveau = !Files.exists(journal);
        StringBuilder sb = new StringBuilder();
        if (nouveau) {
            sb.append('\uFEFF'); // BOM UTF-8 : Excel lit correctement les accents
            sb.append("Date génération;Client;Installation ID;Type;Début;Expiration\r\n");
        }
        sb.append(LicenseDates.formater(jourGeneration)).append(';')
                .append(proteger(client)).append(';')
                .append(licence.getInstallationId()).append(';')
                .append(licence.estAVie() ? "Lifetime" : "Limitée").append(';')
                .append(LicenseDates.formater(licence.getJourDebut())).append(';')
                .append(licence.estAVie() ? "Sans expiration"
                        : LicenseDates.formater(licence.getJourFin()))
                .append("\r\n");
        Files.write(journal, sb.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String proteger(String texte) {
        String t = texte == null ? "" : texte.trim().replace("\"", "\"\"");
        if (t.startsWith("=") || t.startsWith("+") || t.startsWith("-") || t.startsWith("@")) {
            t = "'" + t; // évite qu'Excel interprète le texte comme une formule
        }
        return t.contains(";") || t.contains("\"") || t.contains("\n") ? "\"" + t + "\"" : t;
    }

    /** Résumé lisible : « du 18/10/2026 au 18/10/2027 ». */
    public static String resumePeriode(LicensePayload l) {
        return l.estAVie()
                ? String.format(Locale.ROOT, "à partir du %s (sans expiration)",
                LicenseDates.formater(l.getJourDebut()))
                : String.format(Locale.ROOT, "du %s au %s",
                LicenseDates.formater(l.getJourDebut()), LicenseDates.formater(l.getJourFin()));
    }
}