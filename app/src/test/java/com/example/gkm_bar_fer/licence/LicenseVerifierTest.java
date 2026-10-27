package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.gkm_bar_fer.licence.LicenseVerifier.Resultat;

import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;

public class LicenseVerifierTest {

    private static final String MON_ID = "FP-A7K2-98MX-42PQ";
    private static final String AUTRE_ID = "FP-B3NR-74TW-56YZ";

    private static final int AUJOURDHUI = LicenseDates.jourEpoch(2026, 10, 19);

    private KeyPair cles;
    private LicenseVerifier verifier;

    @Before
    public void preparer() throws Exception {
        cles = LicenseTestSupport.nouvelleCle();
        verifier = new LicenseVerifier(cles.getPublic());
    }

    private String licenceUnAn() throws Exception {
        int debut = LicenseDates.jourEpoch(2026, 10, 18);
        int fin = LicenseDates.ajouterMois(debut, 12);
        return LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.limitee(MON_ID, debut, fin));
    }

    // TEST 2 : code valide
    @Test
    public void codeValide_estAccepte() throws Exception {
        LicenseVerifier.Verification v = verifier.verifier(licenceUnAn(), MON_ID, AUJOURDHUI);
        assertEquals(Resultat.OK, v.getResultat());
        assertEquals(LicenseDates.jourEpoch(2027, 10, 18), v.getLicence().getJourFin());
    }

    @Test
    public void codeEstCourtEtRecopiable() throws Exception {
        String code = licenceUnAn();
        assertTrue("Code trop long : " + code.length(), code.length() < 180);
    }

    // TEST 3 : code invalide
    @Test
    public void codeInvalide_estRefuse() {
        assertEquals(Resultat.FORMAT_INVALIDE, verifier.verifier(null, MON_ID, AUJOURDHUI).getResultat());
        assertEquals(Resultat.FORMAT_INVALIDE, verifier.verifier("", MON_ID, AUJOURDHUI).getResultat());
        assertEquals(Resultat.FORMAT_INVALIDE, verifier.verifier("123456", MON_ID, AUJOURDHUI).getResultat());
        assertEquals(Resultat.FORMAT_INVALIDE,
                verifier.verifier("ABCDEF-GHIJKL-MNOPQR", MON_ID, AUJOURDHUI).getResultat());
    }

    @Test
    public void codeModifie_estRefuse() throws Exception {
        String code = licenceUnAn();
        // On change un caractère au milieu du code (pas le dernier : il peut contenir des bits inutilisés).
        char original = code.charAt(5);
        char change = original == 'A' ? 'B' : 'A';
        String falsifie = code.substring(0, 5) + change + code.substring(6);
        assertEquals(Resultat.SIGNATURE_INVALIDE,
                verifier.verifier(falsifie, MON_ID, AUJOURDHUI).getResultat());
    }

    @Test
    public void codeSigneAvecUneAutreCle_estRefuse() throws Exception {
        KeyPair intrus = LicenseTestSupport.nouvelleCle();
        String faux = LicenseTestSupport.signer(intrus.getPrivate(),
                LicensePayload.aVie(MON_ID, LicenseDates.jourEpoch(2026, 10, 18)));
        assertEquals(Resultat.SIGNATURE_INVALIDE,
                verifier.verifier(faux, MON_ID, AUJOURDHUI).getResultat());
    }

    // TEST 4 : code d'une autre Installation ID
    @Test
    public void codeAutreAppareil_estRefuse() throws Exception {
        assertEquals(Resultat.APPAREIL_INCORRECT,
                verifier.verifier(licenceUnAn(), AUTRE_ID, AUJOURDHUI).getResultat());
        assertEquals(Resultat.APPAREIL_INCORRECT,
                verifier.verifier(licenceUnAn(), null, AUJOURDHUI).getResultat());
    }

    @Test
    public void autreProduit_estRefuse() throws Exception {
        byte[] contenu = LicensePayload.limitee(MON_ID,
                LicenseDates.jourEpoch(2026, 10, 18),
                LicenseDates.jourEpoch(2027, 10, 18)).toBytes();
        contenu[1] = 2; // produit "2" signé correctement, mais ce n'est pas FER_PRO
        String code = LicenseTestSupport.signerOctets(cles.getPrivate(), contenu);
        assertEquals(Resultat.PRODUIT_INCORRECT,
                verifier.verifier(code, MON_ID, AUJOURDHUI).getResultat());
    }

    // TEST 5 : licence expirée
    @Test
    public void licenceExpiree_estDetectee() throws Exception {
        String code = licenceUnAn();
        int dernierJour = LicenseDates.jourEpoch(2027, 10, 18);
        assertEquals(Resultat.OK, verifier.verifier(code, MON_ID, dernierJour).getResultat());
        LicenseVerifier.Verification v = verifier.verifier(code, MON_ID, dernierJour + 1);
        assertEquals(Resultat.EXPIREE, v.getResultat());
        assertEquals("18/10/2027", LicenseDates.formater(v.getLicence().getJourFin()));
    }

    // TEST 6 et 10 : renouvellements successifs
    @Test
    public void renouvellements_successifs() throws Exception {
        int d1 = LicenseDates.jourEpoch(2026, 10, 18);
        String an1 = LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.limitee(MON_ID, d1, LicenseDates.ajouterMois(d1, 12)));
        int d2 = LicenseDates.jourEpoch(2027, 10, 19);
        String an2 = LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.limitee(MON_ID, d2, LicenseDates.ajouterMois(d2, 12)));
        int d3 = LicenseDates.jourEpoch(2028, 10, 20);
        String an3 = LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.limitee(MON_ID, d3, LicenseDates.ajouterMois(d3, 12)));

        int mi2028 = LicenseDates.jourEpoch(2028, 3, 1);
        assertEquals(Resultat.EXPIREE, verifier.verifier(an1, MON_ID, mi2028).getResultat());
        assertEquals(Resultat.OK, verifier.verifier(an2, MON_ID, mi2028).getResultat());

        int mi2029 = LicenseDates.jourEpoch(2029, 3, 1);
        assertEquals(Resultat.EXPIREE, verifier.verifier(an2, MON_ID, mi2029).getResultat());
        assertEquals(Resultat.OK, verifier.verifier(an3, MON_ID, mi2029).getResultat());
    }

    @Test
    public void licenceAVie_nExpireJamais() throws Exception {
        String code = LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.aVie(MON_ID, LicenseDates.jourEpoch(2026, 10, 18)));
        int an2150 = LicenseDates.jourEpoch(2150, 1, 1);
        assertEquals(Resultat.OK, verifier.verifier(code, MON_ID, an2150).getResultat());
        assertTrue(verifier.verifier(code, MON_ID, AUJOURDHUI).getLicence().estAVie());
    }

    @Test
    public void dateDeDebutDansLeFutur() throws Exception {
        int demain = AUJOURDHUI + 1;
        int dans5Jours = AUJOURDHUI + 5;
        String codeDemain = LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.limitee(MON_ID, demain, demain + 365));
        String codeLointain = LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.limitee(MON_ID, dans5Jours, dans5Jours + 365));
        assertEquals(Resultat.OK, verifier.verifier(codeDemain, MON_ID, AUJOURDHUI).getResultat());
        assertEquals(Resultat.PAS_ENCORE_VALIDE,
                verifier.verifier(codeLointain, MON_ID, AUJOURDHUI).getResultat());
    }

    @Test
    public void codeCopieDepuisWhatsApp_estAccepte() throws Exception {
        String code = licenceUnAn();
        String minuscules = code.toLowerCase();
        String avecEspaces = "  " + code.replace("-", " ") + "\n";
        String surPlusieursLignes = code.replace("-", "\n");
        assertEquals(Resultat.OK, verifier.verifier(minuscules, MON_ID, AUJOURDHUI).getResultat());
        assertEquals(Resultat.OK, verifier.verifier(avecEspaces, MON_ID, AUJOURDHUI).getResultat());
        assertEquals(Resultat.OK, verifier.verifier(surPlusieursLignes, MON_ID, AUJOURDHUI).getResultat());
    }

    @Test
    public void cleIntegreeVide_donneNull() {
        // Tant que la vraie clé publique n'est pas dans LicenseConstants (étape 5).
        if (LicenseConstants.PUBLIC_KEY_BASE32.isEmpty()) {
            assertNull(LicenseVerifier.integre());
        } else {
            assertNotNull(LicenseVerifier.integre());
        }
    }

    @Test
    public void payload_refuseLesDonneesInvalides() {
        int debut = LicenseDates.jourEpoch(2026, 10, 18);
        assertThrows(() -> LicensePayload.limitee("FP-0000-0000-0000", debut, debut + 30));
        assertThrows(() -> LicensePayload.limitee(MON_ID, debut, debut - 1));
        assertThrows(() -> LicensePayload.limitee(MON_ID, LicenseDates.jourEpoch(2019, 1, 1), debut));
        assertNull(LicensePayload.fromBytes(new byte[5]));
        assertNull(LicensePayload.fromBytes(null));
    }

    @Test
    public void payload_aller_retour() {
        int debut = LicenseDates.jourEpoch(2026, 10, 18);
        LicensePayload p = LicensePayload.limitee(MON_ID, debut, debut + 90);
        LicensePayload relu = LicensePayload.fromBytes(p.toBytes());
        assertNotNull(relu);
        assertEquals(MON_ID, relu.getInstallationId());
        assertEquals(debut, relu.getJourDebut());
        assertEquals(debut + 90, relu.getJourFin());
        assertFalse(relu.estAVie());
    }

    private interface Action {
        void run();
    }

    private static void assertThrows(Action action) {
        try {
            action.run();
        } catch (IllegalArgumentException attendu) {
            return;
        }
        throw new AssertionError("Une IllegalArgumentException était attendue.");
    }
}