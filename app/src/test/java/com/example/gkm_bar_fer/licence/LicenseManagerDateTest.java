package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.gkm_bar_fer.licence.LicenseManager.ActivationResult;

import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Teste la protection contre les changements de date, avec la vraie chaîne
 * (licence signée + LicenseManager + ClockGuard signé comme avec le Keystore).
 */
public class LicenseManagerDateTest {

    private static final long HEURE = 3_600_000L;
    private static final long JOUR = 24 * HEURE;

    private KeyPair cles;
    private FakeSharedPreferences prefs;
    private FakeMac keystore;
    private final long[] maintenant = new long[1];

    @Before
    public void preparer() throws Exception {
        cles = LicenseTestSupport.nouvelleCle();
        prefs = new FakeSharedPreferences();
        keystore = new FakeMac("cle-du-telephone-A");
        definirDate(2026, 10, 19, 12);
    }

    private void definirDate(int annee, int mois, int jour, int heure) {
        maintenant[0] = LicenseDates.jourEpoch(annee, mois, jour) * JOUR + heure * HEURE;
    }

    private LicenseManager ouvrirApplication() {
        return new LicenseManager(prefs, new LicenseVerifier(cles.getPublic()),
                new LicenseManager.Horloge() {
                    @Override
                    public long maintenantMs() {
                        return maintenant[0];
                    }
                }, keystore);
    }

    private String licence(String id, int[] debut, int[] fin) throws Exception {
        return LicenseTestSupport.signer(cles.getPrivate(), LicensePayload.limitee(id,
                LicenseDates.jourEpoch(debut[0], debut[1], debut[2]),
                LicenseDates.jourEpoch(fin[0], fin[1], fin[2])));
    }

    private String licenceAVie(String id, int[] debut) throws Exception {
        return LicenseTestSupport.signer(cles.getPrivate(), LicensePayload.aVie(id,
                LicenseDates.jourEpoch(debut[0], debut[1], debut[2])));
    }

    // TEST 11 : le cas principal : revenir en arrière pour échapper à l'expiration
    @Test
    public void reculerLaDateApresExpiration_nRedonnePasAcces() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licence(lm.getInstallationId(),
                new int[]{2026, 10, 18}, new int[]{2026, 10, 20})).estSucces());

        definirDate(2026, 10, 22, 9); // la licence a expiré
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());

        definirDate(2026, 10, 15, 9); // l'utilisateur recule la date "avant l'expiration"
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());
        assertFalse(ouvrirApplication().getStatus().autoriseAcces());

        definirDate(2026, 10, 22, 10); // la vraie date revient
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());
    }

    @Test
    public void reculerLaDateSansRedemarrerLApp_estAussiDetecte() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licence(lm.getInstallationId(),
                new int[]{2026, 10, 18}, new int[]{2026, 10, 30})).estSucces());
        definirDate(2026, 10, 25, 9);
        assertEquals(LicenseStatus.VALID, lm.getStatus());
        definirDate(2026, 10, 19, 9); // même instance d'application (app jamais fermée)
        assertEquals(LicenseStatus.CLOCK_ERROR, lm.getStatus());
    }

    @Test
    public void petiteCorrectionDHorloge_nePenalisePersonne() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licenceAVie(lm.getInstallationId(), new int[]{2026, 10, 18})).estSucces());
        definirDate(2026, 10, 19, 11); // 1 heure en arrière
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
        definirDate(2026, 10, 19, 2); // 10 heures en arrière : toléré (< 24 h)
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
    }

    @Test
    public void redemarrageDuTelephone_garde_laProtection() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licence(lm.getInstallationId(),
                new int[]{2026, 10, 18}, new int[]{2027, 10, 18})).estSucces());
        definirDate(2026, 12, 1, 9);
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
        definirDate(2026, 11, 1, 9);
        for (int i = 0; i < 3; i++) {
            assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());
        }
    }

    @Test
    public void recollerLAncienCode_nAnnulePasLaProtection() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String ancien = licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2026, 10, 20});
        assertTrue(lm.activer(ancien).estSucces());
        definirDate(2026, 10, 22, 9);
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());

        // La date est reculée à l'intérieur de la période de l'ancienne licence.
        definirDate(2026, 10, 19, 9);
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());

        ActivationResult r = ouvrirApplication().activer(ancien); // même code, date reculée
        assertEquals(ActivationResult.Etat.DATE_INCORRECTE, r.getEtat());
        assertFalse(r.estSucces());
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());
    }

    @Test
    public void recollerLAncienCode_avecUneDateAvantSonDebut_estRefuse() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String ancien = licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2026, 10, 20});
        assertTrue(lm.activer(ancien).estSucces());
        definirDate(2026, 10, 22, 9);
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());

        definirDate(2026, 10, 15, 9); // avant le début de la licence
        ActivationResult r = ouvrirApplication().activer(ancien);
        assertFalse(r.estSucces());
        assertEquals(LicenseVerifier.Resultat.PAS_ENCORE_VALIDE, r.getResultat());
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());
    }

    @Test
    public void unNouveauCodeDuVendeur_remetLesChosesEnOrdre() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String id = lm.getInstallationId();
        assertTrue(lm.activer(licence(id, new int[]{2026, 10, 18}, new int[]{2026, 10, 20})).estSucces());
        definirDate(2026, 10, 22, 9);
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());
        definirDate(2026, 10, 15, 9);
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());

        // Le vendeur fournit un renouvellement (début et fin plus tardifs) : recours légitime.
        ActivationResult r = ouvrirApplication().activer(
                licence(id, new int[]{2026, 10, 15}, new int[]{2027, 10, 15}));
        assertTrue(r.estSucces());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
    }

    @Test
    public void licenceAVie_apresUneDateMiseTropLoin_peutEtreDebloqueeParUnNouveauCode() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String id = lm.getInstallationId();
        assertTrue(lm.activer(licenceAVie(id, new int[]{2026, 10, 18})).estSucces());
        definirDate(2030, 1, 1, 9); // date mise 3 ans trop loin
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
        definirDate(2026, 10, 19, 9); // on remet la bonne date
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());

        // Le vendeur émet une licence à vie avec une date de début plus récente.
        assertTrue(ouvrirApplication().activer(licenceAVie(id, new int[]{2026, 10, 19})).estSucces());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
    }

    @Test
    public void memoireDeDateFalsifiee_donneErreurDeDate() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licenceAVie(lm.getInstallationId(), new int[]{2026, 10, 18})).estSucces());
        definirDate(2026, 11, 20, 9);
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());

        // Quelqu'un rabaisse la date mémorisée dans le fichier (sans la clé Keystore).
        prefs.edit().putString(ClockGuard.KEY_LAST_SEEN,
                Long.toString(LicenseDates.jourEpoch(2026, 10, 1) * JOUR)).commit();
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());
    }

    @Test
    public void licenceEtMemoireCopieesSurUnAutreTelephone_sontRefusees() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licenceAVie(lm.getInstallationId(), new int[]{2026, 10, 18})).estSucces());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());

        keystore = new FakeMac("cle-du-telephone-B"); // les préférences sont copiées sur un autre téléphone
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());
    }

    @Test
    public void ancienneInstallationSansMemoireDeDate_estAcceptee() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licenceAVie(lm.getInstallationId(), new int[]{2026, 10, 18})).estSucces());
        prefs.edit().remove(ClockGuard.KEY_LAST_SEEN).remove(ClockGuard.KEY_LAST_SEEN_MAC).commit();
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
    }

    @Test
    public void licenceEtDateNeTouchentPasAuxDonneesMetier() throws Exception {
        LicenseManager lm = ouvrirApplication();
        lm.activer(licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2027, 10, 18}));
        definirDate(2030, 1, 1, 9);
        ouvrirApplication().getStatus();
        definirDate(2026, 1, 1, 9);
        ouvrirApplication().getStatus();
        assertEquals(new HashSet<>(Arrays.asList(
                        LicenseConstants.KEY_INSTALLATION_ID, LicenseConstants.KEY_LICENSE_CODE,
                        ClockGuard.KEY_LAST_SEEN, ClockGuard.KEY_LAST_SEEN_MAC)),
                prefs.cles());
    }
}