package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.gkm_bar_fer.licence.LicenseManager.ActivationResult;

import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;

/**
 * Renouvellement AVANT l'expiration (depuis Paramètres) : un code qui ne prolonge pas la
 * licence en cours ne doit jamais la raccourcir ni la remplacer.
 */
public class LicenseManagerRenewalTest {

    private static final long JOUR = 86_400_000L;

    private KeyPair cles;
    private FakeSharedPreferences prefs;
    private final long[] maintenant = new long[1];
    private String id;

    @Before
    public void preparer() throws Exception {
        cles = LicenseTestSupport.nouvelleCle();
        prefs = new FakeSharedPreferences();
        definirDate(2026, 10, 19);
        id = ouvrirApplication().getInstallationId();
    }

    private void definirDate(int annee, int mois, int jour) {
        maintenant[0] = LicenseDates.jourEpoch(annee, mois, jour) * JOUR + 12 * 3_600_000L;
    }

    private LicenseManager ouvrirApplication() {
        return new LicenseManager(prefs, new LicenseVerifier(cles.getPublic()),
                new LicenseManager.Horloge() {
                    @Override
                    public long maintenantMs() {
                        return maintenant[0];
                    }
                }, new FakeMac("cle-du-telephone-A"));
    }

    private String limitee(int[] debut, int[] fin) throws Exception {
        return LicenseTestSupport.signer(cles.getPrivate(), LicensePayload.limitee(id,
                LicenseDates.jourEpoch(debut[0], debut[1], debut[2]),
                LicenseDates.jourEpoch(fin[0], fin[1], fin[2])));
    }

    private String aVie(int[] debut) throws Exception {
        return LicenseTestSupport.signer(cles.getPrivate(), LicensePayload.aVie(id,
                LicenseDates.jourEpoch(debut[0], debut[1], debut[2])));
    }

    private int finActuelle() {
        return ouvrirApplication().getLicence().getJourFin();
    }

    @Test
    public void prolongerAvantExpiration_fonctionne() throws Exception {
        String un_an = limitee(new int[]{2026, 10, 18}, new int[]{2027, 10, 18});
        assertTrue(ouvrirApplication().activer(un_an).estSucces());

        String deux_ans = limitee(new int[]{2026, 10, 19}, new int[]{2028, 10, 19});
        ActivationResult r = ouvrirApplication().activer(deux_ans);
        assertTrue(r.estSucces());
        assertEquals(LicenseDates.jourEpoch(2028, 10, 19), finActuelle());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
    }

    @Test
    public void ancienCodeMoinsLong_neRaccourcitPasLaLicence() throws Exception {
        assertTrue(ouvrirApplication().activer(
                limitee(new int[]{2026, 10, 18}, new int[]{2027, 10, 18})).estSucces());

        // Le client colle par erreur un code d'1 mois, délivré plus tard (début plus récent).
        String unMois = limitee(new int[]{2026, 10, 19}, new int[]{2026, 11, 19});
        ActivationResult r = ouvrirApplication().activer(unMois);

        assertFalse(r.estSucces());
        assertEquals(ActivationResult.Etat.NON_PROLONGEE, r.getEtat());
        assertEquals(LicenseDates.jourEpoch(2027, 10, 18), r.getLicence().getJourFin());
        assertEquals(LicenseDates.jourEpoch(2027, 10, 18), finActuelle()); // inchangée
    }

    @Test
    public void memeCodeRecolle_estSansEffet_et_reussit() throws Exception {
        String code = limitee(new int[]{2026, 10, 18}, new int[]{2027, 10, 18});
        assertTrue(ouvrirApplication().activer(code).estSucces());
        ActivationResult r = ouvrirApplication().activer(code);
        assertTrue(r.estSucces());
        assertEquals(LicenseDates.jourEpoch(2027, 10, 18), finActuelle());
    }

    @Test
    public void memePeriodeAvecUneAutreSignature_nestPasUneProlongation() throws Exception {
        assertTrue(ouvrirApplication().activer(
                limitee(new int[]{2026, 10, 18}, new int[]{2027, 10, 18})).estSucces());
        String reemission = limitee(new int[]{2026, 10, 18}, new int[]{2027, 10, 18});
        ActivationResult r = ouvrirApplication().activer(reemission);
        // Même début et même fin : rien à prolonger. Un code identique est accepté sans effet,
        // et une réémission (autre signature, mêmes dates) est refusée sans conséquence.
        assertTrue(r.estSucces() || r.getEtat() == ActivationResult.Etat.NON_PROLONGEE);
        assertEquals(LicenseDates.jourEpoch(2027, 10, 18), finActuelle());
    }

    @Test
    public void licenceLimitee_puisAVie_estUneProlongation() throws Exception {
        assertTrue(ouvrirApplication().activer(
                limitee(new int[]{2026, 10, 18}, new int[]{2027, 10, 18})).estSucces());
        assertTrue(ouvrirApplication().activer(aVie(new int[]{2026, 10, 19})).estSucces());
        assertTrue(ouvrirApplication().getLicence().estAVie());
        assertEquals(-1, ouvrirApplication().getJoursRestants());
    }

    @Test
    public void licenceAVie_neSeLaissePasRemplacerParUneLimitee() throws Exception {
        assertTrue(ouvrirApplication().activer(aVie(new int[]{2026, 10, 18})).estSucces());
        ActivationResult r = ouvrirApplication().activer(
                limitee(new int[]{2026, 10, 19}, new int[]{2030, 10, 19}));
        assertEquals(ActivationResult.Etat.NON_PROLONGEE, r.getEtat());
        assertTrue(ouvrirApplication().getLicence().estAVie());
    }

    @Test
    public void licenceExpiree_accepteTousLesNouveauxCodes() throws Exception {
        assertTrue(ouvrirApplication().activer(
                limitee(new int[]{2026, 10, 18}, new int[]{2026, 10, 20})).estSucces());
        definirDate(2026, 11, 5);
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());
        // Même un code court est accepté : la licence précédente n'est plus active.
        assertTrue(ouvrirApplication().activer(
                limitee(new int[]{2026, 11, 5}, new int[]{2026, 12, 5})).estSucces());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
    }

    @Test
    public void joursRestants_sontCorrects() throws Exception {
        assertEquals(-1, ouvrirApplication().getJoursRestants()); // aucune licence
        assertTrue(ouvrirApplication().activer(
                limitee(new int[]{2026, 10, 18}, new int[]{2026, 10, 29})).estSucces());
        assertEquals(10, ouvrirApplication().getJoursRestants()); // 19/10 -> 29/10
        definirDate(2026, 10, 29);
        assertEquals(0, ouvrirApplication().getJoursRestants()); // dernier jour
        definirDate(2026, 11, 3);
        assertEquals(0, ouvrirApplication().getJoursRestants()); // expirée : jamais négatif
    }
}