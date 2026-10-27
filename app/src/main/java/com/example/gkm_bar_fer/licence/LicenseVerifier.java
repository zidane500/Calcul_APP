package com.example.gkm_bar_fer.licence;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Vérifie un code de licence avec la clé PUBLIQUE (ECDSA P-256 + SHA-256).
 *
 * Le code est : Base32( contenu de 19 octets + signature DER ).
 * Ordre des contrôles (important pour la sécurité) :
 *   1. format du code, 2. SIGNATURE (avant de faire confiance au contenu),
 *   3. produit, 4. Installation ID, 5. dates.
 *
 * Classe 100 % Java (aucune dépendance Android) : testable sur PC.
 */
public final class LicenseVerifier {

    public enum Resultat {
        OK,
        FORMAT_INVALIDE,
        SIGNATURE_INVALIDE,
        PRODUIT_INCORRECT,
        APPAREIL_INCORRECT,
        PAS_ENCORE_VALIDE,
        EXPIREE
    }

    /** Résultat d'une vérification. */
    public static final class Verification {
        private final Resultat resultat;
        private final LicensePayload licence;

        Verification(Resultat resultat, LicensePayload licence) {
            this.resultat = resultat;
            this.licence = licence;
        }

        public Resultat getResultat() {
            return resultat;
        }

        /**
         * Contenu de la licence. Disponible seulement si la signature est authentique
         * (donc pour OK, EXPIREE, PAS_ENCORE_VALIDE, APPAREIL_INCORRECT, PRODUIT_INCORRECT).
         */
        public LicensePayload getLicence() {
            return licence;
        }

        public boolean estOk() {
            return resultat == Resultat.OK;
        }
    }

    private static final int SIGNATURE_MIN = 8;
    private static final int SIGNATURE_MAX = 80;

    private final PublicKey clePublique;

    public LicenseVerifier(PublicKey clePublique) {
        if (clePublique == null) {
            throw new IllegalArgumentException("Clé publique absente.");
        }
        this.clePublique = clePublique;
    }

    /** Construit un vérificateur à partir d'une clé publique X.509 encodée en Base32. */
    public static LicenseVerifier depuisBase32(String clePubliqueBase32)
            throws GeneralSecurityException {
        byte[] x509 = Base32.decode(clePubliqueBase32);
        if (x509 == null || x509.length == 0) {
            throw new GeneralSecurityException("Clé publique illisible.");
        }
        PublicKey cle = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(x509));
        return new LicenseVerifier(cle);
    }

    /**
     * Vérificateur avec la clé publique intégrée à l'application.
     *
     * @return null si aucune clé publique valide n'est configurée (tout sera alors refusé).
     */
    public static LicenseVerifier integre() {
        try {
            return depuisBase32(LicenseConstants.PUBLIC_KEY_BASE32);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    /**
     * @param code                  le code de licence saisi (espaces, tirets, minuscules acceptés)
     * @param installationIdLocal   l'Installation ID de ce téléphone
     * @param jourActuel            jour epoch (UTC) d'aujourd'hui
     */
    public Verification verifier(String code, String installationIdLocal, int jourActuel) {
        byte[] tout = Base32.decode(code);
        if (tout == null || tout.length < LicensePayload.TAILLE + SIGNATURE_MIN
                || tout.length > LicensePayload.TAILLE + SIGNATURE_MAX) {
            return new Verification(Resultat.FORMAT_INVALIDE, null);
        }
        byte[] contenu = Arrays.copyOfRange(tout, 0, LicensePayload.TAILLE);
        byte[] signature = Arrays.copyOfRange(tout, LicensePayload.TAILLE, tout.length);

        if (!signatureValide(contenu, signature)) {
            return new Verification(Resultat.SIGNATURE_INVALIDE, null);
        }

        LicensePayload licence = LicensePayload.fromBytes(contenu);
        if (licence == null) {
            return new Verification(Resultat.FORMAT_INVALIDE, null);
        }
        if (licence.getProduitId() != LicenseConstants.PRODUCT_ID) {
            return new Verification(Resultat.PRODUIT_INCORRECT, licence);
        }
        if (installationIdLocal == null
                || !licence.getInstallationId().equals(installationIdLocal)) {
            return new Verification(Resultat.APPAREIL_INCORRECT, licence);
        }
        if (licence.getJourDebut() > jourActuel + LicenseConstants.TOLERANCE_DEBUT_JOURS) {
            return new Verification(Resultat.PAS_ENCORE_VALIDE, licence);
        }
        if (licence.estExpireeLe(jourActuel)) {
            return new Verification(Resultat.EXPIREE, licence);
        }
        return new Verification(Resultat.OK, licence);
    }

    private boolean signatureValide(byte[] contenu, byte[] signature) {
        try {
            Signature verif = Signature.getInstance("SHA256withECDSA");
            verif.initVerify(clePublique);
            verif.update(contenu);
            return verif.verify(signature);
        } catch (GeneralSecurityException e) {
            return false; // signature mal formée = signature invalide
        }
    }
}