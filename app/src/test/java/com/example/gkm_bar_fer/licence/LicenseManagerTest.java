package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.gkm_bar_fer.licence.LicenseManager.ActivationResult;
import com.example.gkm_bar_fer.licence.LicenseVerifier.Resultat;

import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Teste le comportement complet de LicenseManager sans téléphone :
 * "téléphone" = FakeSharedPreferences, "heure" = tableau modifiable.
 * Rouvrir l'application = créer un NOUVEAU LicenseManager sur les mêmes préférences.
 */
public class LicenseManagerTest {

    private static final long MS_PAR_JOUR = 86_400_000L;

    private KeyPair cles;
    private FakeSharedPreferences prefs;
    private final long[] maintenant = new long[1];

    @Before
    public void preparer() throws Exception {
        cles = LicenseTestSupport.nouvelleCle();
        prefs = new FakeSharedPreferences();
        definirDate(2026, 10, 19);
    }

    private void definirDate(int annee, int mois, int jour) {
        maintenant[0] = LicenseDates.jourEpoch(annee, mois, jour) * MS_PAR_JOUR + 12 * 3_600_000L;
    }

    /** Équivaut à fermer puis rouvrir l'application (ou redémarrer le téléphone). */
    private LicenseManager ouvrirApplication() {
        return new LicenseManager(prefs, new LicenseVerifier(cles.getPublic()),
                new LicenseManager.Horloge() {
                    @Override
                    public long maintenantMs() {
                        return maintenant[0];
                    }
                });
    }

    private String licence(String id, int[] debut, int[] fin) throws Exception {
        return LicenseTestSupport.signer(cles.getPrivate(), LicensePayload.limitee(id,
                LicenseDates.jourEpoch(debut[0], debut[1], debut[2]),
                LicenseDates.jourEpoch(fin[0], fin[1], fin[2])));
    }

    // TEST 1 : installation sans licence
    @Test
    public void sansLicence_ecranActivation() {
        LicenseManager lm = ouvrirApplication();
        assertEquals(LicenseStatus.NOT_ACTIVATED, lm.getStatus());
        assertFalse(lm.getStatus().autoriseAcces());
        assertNull(lm.getLicence());
    }

    @Test
    public void installationId_estStableApresRedemarrage() {
        String id = ouvrirApplication().getInstallationId();
        assertTrue(InstallationId.estValide(id));
        assertEquals(id, ouvrirApplication().getInstallationId());
        assertEquals(id, ouvrirApplication().getInstallationId());
    }

    // TEST 2 + 8 + 9 : code valide, puis fermer/rouvrir, puis redémarrer
    @Test
    public void codeValide_active_et_reste_actif_apresRedemarrage() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String code = licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2027, 10, 18});

        ActivationResult r = lm.activer(code);
        assertTrue(r.estSucces());
        assertEquals(LicenseStatus.VALID, lm.getStatus());

        // Fermer et rouvrir l'application, plusieurs fois
        for (int i = 0; i < 3; i++) {
            LicenseManager rouverte = ouvrirApplication();
            assertEquals(LicenseStatus.VALID, rouverte.getStatus());
            assertTrue(rouverte.getStatus().autoriseAcces());
        }
        assertEquals("18/10/2027",
                LicenseDates.formater(ouvrirApplication().getLicence().getJourFin()));
    }

    // TEST 3 : code invalide
    @Test
    public void codeInvalide_refuse_et_rienNEstEnregistre() {
        LicenseManager lm = ouvrirApplication();
        for (String mauvais : new String[]{"", "   ", null, "123", "ABCDEF-GHIJKL-MNOPQR"}) {
            ActivationResult r = lm.activer(mauvais);
            assertFalse(r.estSucces());
            assertEquals(ActivationResult.Etat.REFUSE, r.getEtat());
            assertEquals(Resultat.FORMAT_INVALIDE, r.getResultat());
        }
        assertEquals(LicenseStatus.NOT_ACTIVATED, ouvrirApplication().getStatus());
        assertFalse(prefs.contains(LicenseConstants.KEY_LICENSE_CODE));
    }

    @Test
    public void codeFalsifie_refuse() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String code = licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2027, 10, 18});
        char c = code.charAt(5);
        String falsifie = code.substring(0, 5) + (c == 'A' ? 'B' : 'A') + code.substring(6);
        ActivationResult r = lm.activer(falsifie);
        assertFalse(r.estSucces());
        assertEquals(Resultat.SIGNATURE_INVALIDE, r.getResultat());
        assertEquals(LicenseStatus.NOT_ACTIVATED, lm.getStatus());
    }

    // TEST 4 : code d'une autre Installation ID
    @Test
    public void codeAutreTelephone_refuse() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String codePourUnAutre = licence("FP-B3NR-74TW-56YZ",
                new int[]{2026, 10, 18}, new int[]{2027, 10, 18});
        ActivationResult r = lm.activer(codePourUnAutre);
        assertFalse(r.estSucces());
        assertEquals(Resultat.APPAREIL_INCORRECT, r.getResultat());
        assertEquals(LicenseStatus.NOT_ACTIVATED, lm.getStatus());
        assertFalse(prefs.contains(LicenseConstants.KEY_LICENSE_CODE));
    }

    // TEST 5 : licence expirée
    @Test
    public void licenceExpiree_ecranRenouvellement() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licence(lm.getInstallationId(),
                new int[]{2026, 10, 18}, new int[]{2026, 10, 20})).estSucces());

        definirDate(2026, 10, 20);
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus()); // dernier jour inclus

        definirDate(2026, 10, 21);
        LicenseManager rouverte = ouvrirApplication();
        assertEquals(LicenseStatus.EXPIRED, rouverte.getStatus());
        assertFalse(rouverte.getStatus().autoriseAcces());
        assertEquals("20/10/2026", LicenseDates.formater(rouverte.getLicence().getJourFin()));
    }

    @Test
    public void codeDejaExpire_refuseALActivation() throws Exception {
        LicenseManager lm = ouvrirApplication();
        ActivationResult r = lm.activer(licence(lm.getInstallationId(),
                new int[]{2025, 1, 1}, new int[]{2025, 12, 31}));
        assertFalse(r.estSucces());
        assertEquals(Resultat.EXPIREE, r.getResultat());
    }

    // TEST 6 + 7 + 10 : renouvellements successifs, données conservées
    @Test
    public void renouvellementsSuccessifs_fonctionnentEtGardentLInstallationId() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String id = lm.getInstallationId();

        assertTrue(lm.activer(licence(id, new int[]{2026, 10, 18}, new int[]{2026, 11, 18})).estSucces());
        definirDate(2026, 11, 25);
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());

        assertTrue(ouvrirApplication().activer(licence(id, new int[]{2026, 11, 25}, new int[]{2027, 11, 25})).estSucces());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());

        definirDate(2027, 12, 1);
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());

        assertTrue(ouvrirApplication().activer(licence(id, new int[]{2027, 12, 1}, new int[]{2028, 12, 1})).estSucces());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());

        definirDate(2029, 1, 1);
        assertEquals(LicenseStatus.EXPIRED, ouvrirApplication().getStatus());
        assertTrue(ouvrirApplication().activer(LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.aVie(id, LicenseDates.jourEpoch(2029, 1, 1)))).estSucces());
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());

        assertEquals(id, ouvrirApplication().getInstallationId());
    }

    @Test
    public void licenceNeTouchePasAuxDonneesMetier() throws Exception {
        LicenseManager lm = ouvrirApplication();
        lm.activer(licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2027, 10, 18}));
        definirDate(2030, 1, 1);
        ouvrirApplication().getStatus();
        // Le gestionnaire n'écrit QUE ces clés, dans son propre fichier license_prefs.
        // (last_seen_ms = mémoire de la dernière date vue, protection contre le retour de date)
        assertEquals(new HashSet<>(Arrays.asList(
                        LicenseConstants.KEY_INSTALLATION_ID, LicenseConstants.KEY_LICENSE_CODE,
                        ClockGuard.KEY_LAST_SEEN)),
                prefs.cles());
    }

    @Test
    public void licenceFalsifieeDansLeStockage_estDetectee() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String code = licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2026, 10, 25});
        assertTrue(lm.activer(code).estSucces());

        // Quelqu'un modifie le fichier de préférences : remplace la licence par un code modifié.
        String stocke = prefs.getString(LicenseConstants.KEY_LICENSE_CODE, null);
        char c = stocke.charAt(10);
        prefs.edit().putString(LicenseConstants.KEY_LICENSE_CODE,
                stocke.substring(0, 10) + (c == 'A' ? 'B' : 'A') + stocke.substring(11)).commit();
        assertEquals(LicenseStatus.INVALID, ouvrirApplication().getStatus());
    }

    @Test
    public void licenceCopieeSurUnAutreTelephone_estRefusee() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(licence(lm.getInstallationId(),
                new int[]{2026, 10, 18}, new int[]{2027, 10, 18})).estSucces());
        String codeStocke = prefs.getString(LicenseConstants.KEY_LICENSE_CODE, null);

        // "Autre téléphone" : mêmes préférences copiées, mais un autre Installation ID.
        FakeSharedPreferences autre = new FakeSharedPreferences();
        autre.edit().putString(LicenseConstants.KEY_LICENSE_CODE, codeStocke)
                .putString(LicenseConstants.KEY_INSTALLATION_ID, "FP-B3NR-74TW-56YZ").commit();
        LicenseManager surAutre = new LicenseManager(autre, new LicenseVerifier(cles.getPublic()),
                new LicenseManager.Horloge() {
                    @Override
                    public long maintenantMs() {
                        return maintenant[0];
                    }
                });
        assertEquals(LicenseStatus.INVALID, surAutre.getStatus());
    }

    @Test
    public void dateTelephoneRecule_donneErreurDeDate() throws Exception {
        LicenseManager lm = ouvrirApplication();
        // Licence activée le 19/10/2026 qui commence le 19/10/2026
        assertTrue(lm.activer(licence(lm.getInstallationId(),
                new int[]{2026, 10, 19}, new int[]{2027, 10, 19})).estSucces());
        definirDate(2026, 10, 10); // le téléphone est reculé de 9 jours
        assertEquals(LicenseStatus.CLOCK_ERROR, ouvrirApplication().getStatus());
        definirDate(2026, 10, 19);
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
    }

    @Test
    public void licenceAVie_nExpireJamais() throws Exception {
        LicenseManager lm = ouvrirApplication();
        assertTrue(lm.activer(LicenseTestSupport.signer(cles.getPrivate(),
                LicensePayload.aVie(lm.getInstallationId(), LicenseDates.jourEpoch(2026, 10, 18)))).estSucces());
        definirDate(2150, 6, 1);
        assertEquals(LicenseStatus.VALID, ouvrirApplication().getStatus());
        assertTrue(ouvrirApplication().getLicence().estAVie());
    }

    @Test
    public void codeCopieAvecEspacesOuMinuscules_estAccepte_et_stockeProprement() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String code = licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2027, 10, 18});
        assertTrue(lm.activer("  " + code.toLowerCase().replace("-", "\n") + "  ").estSucces());
        assertEquals(code, prefs.getString(LicenseConstants.KEY_LICENSE_CODE, null));
    }

    @Test
    public void sansClePublique_toutEstRefuse() throws Exception {
        LicenseManager sansCle = new LicenseManager(prefs, null, new LicenseManager.Horloge() {
            @Override
            public long maintenantMs() {
                return maintenant[0];
            }
        });
        ActivationResult r = sansCle.activer("peu importe");
        assertEquals(ActivationResult.Etat.CONFIGURATION_MANQUANTE, r.getEtat());
        assertFalse(r.estSucces());
        assertEquals(LicenseStatus.NOT_ACTIVATED, sansCle.getStatus());
    }

    @Test
    public void ecritureEnEchec_n_annonce_pas_un_succes() throws Exception {
        LicenseManager lm = ouvrirApplication();
        String code = licence(lm.getInstallationId(), new int[]{2026, 10, 18}, new int[]{2027, 10, 18});
        prefs.ecritureEnEchec = true;
        ActivationResult r = lm.activer(code);
        assertEquals(ActivationResult.Etat.ERREUR_STOCKAGE, r.getEtat());
        assertFalse(r.estSucces());
        prefs.ecritureEnEchec = false;
        assertEquals(LicenseStatus.NOT_ACTIVATED, lm.getStatus());
        assertNotNull(lm.getInstallationId());
    }
}