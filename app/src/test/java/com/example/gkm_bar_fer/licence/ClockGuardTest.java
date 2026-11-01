package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.gkm_bar_fer.licence.ClockGuard.Verdict;

import org.junit.Before;
import org.junit.Test;

public class ClockGuardTest {

    private static final String ID = "FP-A7K2-98MX-42PQ";
    private static final long HEURE = 3_600_000L;
    private static final long JOUR = 24 * HEURE;
    private static final long T0 = LicenseDates.jourEpoch(2026, 10, 19) * JOUR + 12 * HEURE;

    private FakeSharedPreferences prefs;
    private FakeMac keystore;

    @Before
    public void preparer() {
        prefs = new FakeSharedPreferences();
        keystore = new FakeMac("cle-du-telephone-A");
    }

    private ClockGuard nouveauProcessus() {
        // Fermer l'application / redémarrer le téléphone = nouvel objet, mêmes préférences.
        return new ClockGuard(prefs, keystore);
    }

    @Test
    public void premiereObservation_estAcceptee_et_enregistree() {
        assertEquals(Verdict.OK, nouveauProcessus().controler(T0, ID));
        assertEquals(Long.toString(T0), prefs.getString(ClockGuard.KEY_LAST_SEEN, null));
        assertNotNull(prefs.getString(ClockGuard.KEY_LAST_SEEN_MAC, null));
    }

    @Test
    public void tempsQuiAvance_estAccepte() {
        ClockGuard g = nouveauProcessus();
        assertEquals(Verdict.OK, g.controler(T0, ID));
        assertEquals(Verdict.OK, g.controler(T0 + 5 * JOUR, ID));
        assertEquals(Verdict.OK, g.controler(T0 + 400 * JOUR, ID));
    }

    @Test
    public void petitRecul_estTolere() {
        ClockGuard g = nouveauProcessus();
        g.controler(T0, ID);
        assertEquals(Verdict.OK, g.controler(T0 - 23 * HEURE, ID));
        assertEquals(Verdict.OK, g.controler(T0 - 2 * HEURE, ID));
    }

    @Test
    public void grandRecul_estDetecte_et_laReferenceNeBougePas() {
        ClockGuard g = nouveauProcessus();
        g.controler(T0, ID);
        assertEquals(Verdict.DATE_RECULEE, g.controler(T0 - 25 * HEURE, ID));
        assertEquals(Verdict.DATE_RECULEE, g.controler(T0 - 10 * JOUR, ID));
        assertEquals(Long.toString(T0), prefs.getString(ClockGuard.KEY_LAST_SEEN, null));
        // Quand la vraie date revient, tout redevient normal.
        assertEquals(Verdict.OK, g.controler(T0 + HEURE, ID));
    }

    // TEST 9 + 11 : fermer l'application, redémarrer le téléphone
    @Test
    public void recul_estDetecte_apresFermetureEtRedemarrage() {
        nouveauProcessus().controler(T0 + 10 * JOUR, ID);
        for (int i = 0; i < 3; i++) {
            assertEquals(Verdict.DATE_RECULEE, nouveauProcessus().controler(T0, ID));
        }
    }

    @Test
    public void avanceEnAvantPuisRetour_estUnFauxPositifDocumente_et_reinitialiserLeCorrige() {
        ClockGuard g = nouveauProcessus();
        g.controler(T0, ID);
        g.controler(T0 + 800 * JOUR, ID); // date mise 2 ans trop loin
        assertEquals(Verdict.DATE_RECULEE, g.controler(T0 + 1 * JOUR, ID));
        g.reinitialiser(T0 + 1 * JOUR, ID); // nouvelle licence plus récente activée
        assertEquals(Verdict.OK, g.controler(T0 + 1 * JOUR, ID));
    }

    @Test
    public void ecrituresLimitees_uneParHeureAuPlus() {
        ClockGuard g = nouveauProcessus();
        g.controler(T0, ID);
        g.controler(T0 + 30 * 60 * 1000L, ID);
        assertEquals(Long.toString(T0), prefs.getString(ClockGuard.KEY_LAST_SEEN, null));
        g.controler(T0 + HEURE, ID);
        assertEquals(Long.toString(T0 + HEURE), prefs.getString(ClockGuard.KEY_LAST_SEEN, null));
    }

    @Test
    public void referenceModifieeSansLaCle_estDetectee() {
        nouveauProcessus().controler(T0 + 10 * JOUR, ID);
        // Quelqu'un édite le fichier et met une date plus ancienne, sans pouvoir recalculer la signature.
        prefs.edit().putString(ClockGuard.KEY_LAST_SEEN, Long.toString(T0 - 100 * JOUR)).commit();
        assertEquals(Verdict.ALTERE, nouveauProcessus().controler(T0, ID));
    }

    @Test
    public void signatureModifiee_estDetectee() {
        nouveauProcessus().controler(T0, ID);
        prefs.edit().putString(ClockGuard.KEY_LAST_SEEN_MAC, "00112233").commit();
        assertEquals(Verdict.ALTERE, nouveauProcessus().controler(T0, ID));
    }

    @Test
    public void valeurIllisible_estDetectee() {
        prefs.edit().putString(ClockGuard.KEY_LAST_SEEN, "n'importe quoi").commit();
        assertEquals(Verdict.ALTERE, nouveauProcessus().controler(T0, ID));
    }

    @Test
    public void memoireCopieeDUnAutreTelephone_estDetectee() {
        nouveauProcessus().controler(T0, ID);
        // Même fichier de préférences, mais un autre téléphone (autre clé Keystore).
        ClockGuard surAutreTelephone = new ClockGuard(prefs, new FakeMac("cle-du-telephone-B"));
        assertEquals(Verdict.ALTERE, surAutreTelephone.controler(T0, ID));
    }

    @Test
    public void memoireLieeAUnAutreInstallationId_estDetectee() {
        nouveauProcessus().controler(T0, ID);
        assertEquals(Verdict.ALTERE, nouveauProcessus().controler(T0, "FP-B3NR-74TW-56YZ"));
    }

    @Test
    public void keystoreIndisponible_nEmpecheJamaisLUtilisation_mais_leReculEstQuandMemeDetecte() {
        keystore.indisponible = true;
        ClockGuard g = nouveauProcessus();
        assertEquals(Verdict.OK, g.controler(T0 + 10 * JOUR, ID));
        assertNull(prefs.getString(ClockGuard.KEY_LAST_SEEN_MAC, null));
        assertEquals(Verdict.DATE_RECULEE, nouveauProcessus().controler(T0, ID));
        assertFalse(prefs.contains(ClockGuard.KEY_LAST_SEEN_MAC));
    }

    @Test
    public void sansSignatureEnregistree_puisKeystoreDisponible_estAccepte() {
        keystore.indisponible = true;
        nouveauProcessus().controler(T0, ID);
        keystore.indisponible = false;
        assertEquals(Verdict.OK, nouveauProcessus().controler(T0 + 2 * HEURE, ID));
        assertNotNull(prefs.getString(ClockGuard.KEY_LAST_SEEN_MAC, null));
    }
}