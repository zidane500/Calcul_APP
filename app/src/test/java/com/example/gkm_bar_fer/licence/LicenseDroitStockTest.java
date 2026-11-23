package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertArrayEquals;
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

/**
 * Droit « utiliser les fonctionnalités du stock » :
 *  - anciens codes (format 1) : stock inclus, octets inchangés ;
 *  - nouveaux codes sans stock (format 2) ;
 *  - impossible de modifier le droit sans casser la signature ;
 *  - passage d'une licence « sans stock » à « avec stock ».
 */
public class LicenseDroitStockTest {

    private static final long JOUR = 86_400_000L;
    private static final String ID = "FP-A7K2-98MX-42PQ";
    private static final int DEBUT = LicenseDates.jourEpoch(2026, 10, 18);
    private static final int FIN = LicenseDates.jourEpoch(2027, 10, 18);
    private static final int AUJOURDHUI = LicenseDates.jourEpoch(2026, 10, 19);

    private KeyPair cles;
    private FakeSharedPreferences prefs;
    private final long[] maintenant = new long[1];
    private String idTelephone;

    @Before
    public void preparer() throws Exception {
        cles = LicenseTestSupport.nouvelleCle();
        prefs = new FakeSharedPreferences();
        maintenant[0] = AUJOURDHUI * JOUR + 12 * 3_600_000L;
        idTelephone = ouvrirApplication().getInstallationId();
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

    private String code(LicensePayload p) throws Exception {
        return LicenseTestSupport.signer(cles.getPrivate(), p);
    }

    // ---------- Format des octets ----------

    @Test
    public void avecStock_donneExactementLeFormatVersion1() {
        byte[] octets = LicensePayload.limitee(ID, DEBUT, FIN, true).toBytes();
        assertEquals(19, octets.length);
        assertEquals(1, octets[0]);
        // Identique à la méthode historique (sans paramètre de droit).
        assertArrayEquals(LicensePayload.limitee(ID, DEBUT, FIN).toBytes(), octets);
        assertArrayEquals(LicensePayload.aVie(ID, DEBUT).toBytes(),
                LicensePayload.aVie(ID, DEBUT, true).toBytes());
    }

    @Test
    public void sansStock_donneLeFormatVersion2() {
        byte[] octets = LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes();
        assertEquals(20, octets.length);
        assertEquals(2, octets[0]);
        assertEquals(0, octets[19]);
        assertEquals(20, LicensePayload.aVie(ID, DEBUT, false).toBytes().length);
    }

    @Test
    public void ancienneLicenceVersion1_accordeLeStock() {
        LicensePayload relue = LicensePayload.fromBytes(LicensePayload.limitee(ID, DEBUT, FIN).toBytes());
        assertNotNull(relue);
        assertTrue(relue.donneDroitStock());
    }

    @Test
    public void licenceSansStock_serelitSansStock() {
        LicensePayload p = LicensePayload.fromBytes(
                LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes());
        assertNotNull(p);
        assertFalse(p.donneDroitStock());
        assertEquals(ID, p.getInstallationId());
        assertEquals(DEBUT, p.getJourDebut());
        assertEquals(FIN, p.getJourFin());
        LicensePayload vie = LicensePayload.fromBytes(LicensePayload.aVie(ID, DEBUT, false).toBytes());
        assertNotNull(vie);
        assertTrue(vie.estAVie());
        assertFalse(vie.donneDroitStock());
    }

    @Test
    public void version2AvecBitStock_estLueAvecStock() {
        byte[] octets = LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes();
        octets[19] = 1;
        assertTrue(LicensePayload.fromBytes(octets).donneDroitStock());
    }

    @Test
    public void bitsDeDroitsInconnus_sontIgnores() {
        byte[] octets = LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes();
        octets[19] = (byte) 0xFE; // bit stock éteint, autres bits inconnus allumés
        LicensePayload p = LicensePayload.fromBytes(octets);
        assertNotNull(p);
        assertFalse(p.donneDroitStock());
    }

    @Test
    public void tailleOuVersionIncorrecte_estRefusee() {
        byte[] v2 = LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes();
        assertNull(LicensePayload.fromBytes(Arrays.copyOf(v2, 19))); // v2 tronquée
        byte[] v1 = LicensePayload.limitee(ID, DEBUT, FIN).toBytes();
        assertNull(LicensePayload.fromBytes(Arrays.copyOf(v1, 20))); // v1 rallongée
        byte[] v3 = Arrays.copyOf(v2, 20);
        v3[0] = 3;
        assertNull(LicensePayload.fromBytes(v3));
    }

    // ---------- Vérification cryptographique ----------

    @Test
    public void codeSansStock_estAccepteParLeVerificateur() throws Exception {
        LicenseVerifier verifier = new LicenseVerifier(cles.getPublic());
        LicenseVerifier.Verification v = verifier.verifier(
                code(LicensePayload.limitee(ID, DEBUT, FIN, false)), ID, AUJOURDHUI);
        assertEquals(Resultat.OK, v.getResultat());
        assertFalse(v.getLicence().donneDroitStock());
    }

    @Test
    public void ancienCode_resteAccepteEtAvecStock() throws Exception {
        LicenseVerifier verifier = new LicenseVerifier(cles.getPublic());
        LicenseVerifier.Verification v = verifier.verifier(
                code(LicensePayload.limitee(ID, DEBUT, FIN)), ID, AUJOURDHUI);
        assertEquals(Resultat.OK, v.getResultat());
        assertTrue(v.getLicence().donneDroitStock());
    }

    @Test
    public void modifierLeDroitDansUnCodeSigne_casseLaSignature() throws Exception {
        // Code SANS stock, signé : on essaie de passer l'octet de droits à 1.
        byte[] contenu = LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes();
        String bon = LicenseTestSupport.signerOctets(cles.getPrivate(), contenu);
        byte[] tout = Base32.decode(bon);
        tout[19] = 1;
        String falsifie = Base32.grouper(Base32.encode(tout), LicenseConstants.TAILLE_GROUPE_CODE);
        LicenseVerifier verifier = new LicenseVerifier(cles.getPublic());
        assertEquals(Resultat.SIGNATURE_INVALIDE,
                verifier.verifier(falsifie, ID, AUJOURDHUI).getResultat());
    }

    @Test
    public void transformerUnCodeSansStockEnVersion1_casseLaSignature() throws Exception {
        // Astuce possible : retirer l'octet de droits et mettre la version à 1 pour obtenir « stock ».
        byte[] contenu = LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes();
        byte[] tout = Base32.decode(LicenseTestSupport.signerOctets(cles.getPrivate(), contenu));
        byte[] astuce = new byte[tout.length - 1];
        astuce[0] = 1;
        System.arraycopy(tout, 1, astuce, 1, 18);          // octets 1..18 inchangés
        System.arraycopy(tout, 20, astuce, 19, tout.length - 20); // signature d'origine
        String code = Base32.grouper(Base32.encode(astuce), LicenseConstants.TAILLE_GROUPE_CODE);
        LicenseVerifier verifier = new LicenseVerifier(cles.getPublic());
        assertEquals(Resultat.SIGNATURE_INVALIDE,
                verifier.verifier(code, ID, AUJOURDHUI).getResultat());
    }

    @Test
    public void versionInconnue_estRefusee() throws Exception {
        byte[] contenu = LicensePayload.limitee(ID, DEBUT, FIN, false).toBytes();
        contenu[0] = 9;
        String code = LicenseTestSupport.signerOctets(cles.getPrivate(), contenu);
        assertEquals(Resultat.FORMAT_INVALIDE,
                new LicenseVerifier(cles.getPublic()).verifier(code, ID, AUJOURDHUI).getResultat());
    }

    @Test
    public void codeSansStock_resteCourtEtRecopiable() throws Exception {
        String code = code(LicensePayload.limitee(ID, DEBUT, FIN, false));
        assertTrue("Code trop long : " + code.length(), code.length() < 185);
    }

    // ---------- Gestionnaire de licence ----------

    @Test
    public void sansLicence_pasDeStock() {
        assertFalse(ouvrirApplication().peutUtiliserStock());
    }

    @Test
    public void licenceAvecStock_autoriseLeStock() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN))).estSucces());
        assertTrue(ouvrirApplication().peutUtiliserStock());
    }

    @Test
    public void licenceSansStock_masqueLeStockMaisLApplicationFonctionne() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN, false))).estSucces());
        LicenseManager m = ouvrirApplication();
        assertEquals(LicenseStatus.VALID, m.getStatus());
        assertFalse(m.peutUtiliserStock());
    }

    @Test
    public void licenceExpiree_pasDeStockMemeSiLeDroitEtaitAccorde() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN))).estSucces());
        maintenant[0] = (FIN + 5) * JOUR + 12 * 3_600_000L;
        LicenseManager m = ouvrirApplication();
        assertEquals(LicenseStatus.EXPIRED, m.getStatus());
        assertFalse(m.peutUtiliserStock());
    }

    @Test
    public void licenceAVieSansStock_pasDeStock() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.aVie(idTelephone, DEBUT, false))).estSucces());
        assertFalse(ouvrirApplication().peutUtiliserStock());
    }

    // ---------- Ajouter le stock plus tard (amélioration) ----------

    @Test
    public void ajouterLeStock_memeDateDeFin_estAccepte() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN, false))).estSucces());
        assertFalse(ouvrirApplication().peutUtiliserStock());

        ActivationResult r = ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN, true)));
        assertTrue("Etat : " + r.getEtat(), r.estSucces());
        assertTrue(ouvrirApplication().peutUtiliserStock());
        assertEquals(FIN, ouvrirApplication().getLicence().getJourFin());
    }

    @Test
    public void ajouterLeStock_surLicenceAVie_estAccepte() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.aVie(idTelephone, DEBUT, false))).estSucces());
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.aVie(idTelephone, DEBUT, true))).estSucces());
        assertTrue(ouvrirApplication().peutUtiliserStock());
    }

    @Test
    public void ajouterLeStock_maisRaccourcirLaPeriode_estRefuse() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN, false))).estSucces());
        int finCourte = LicenseDates.jourEpoch(2027, 1, 18);
        ActivationResult r = ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, finCourte, true)));
        assertEquals(ActivationResult.Etat.NON_PROLONGEE, r.getEtat());
        assertFalse(ouvrirApplication().peutUtiliserStock());
        assertEquals(FIN, ouvrirApplication().getLicence().getJourFin());
    }

    @Test
    public void ancienCodeAvecStock_nePeutPasRemplacerUneLicenceDejaAvecStock() throws Exception {
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN))).estSucces());
        // Même période, mêmes droits : un autre code ne remplace rien.
        ActivationResult r = ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT + 1, FIN)));
        assertEquals(ActivationResult.Etat.NON_PROLONGEE, r.getEtat());
    }

    @Test
    public void renouvellementPlusLongSansStock_retireLeStock() throws Exception {
        // Le vendeur décide : renouvellement 2 ans mais SANS stock. C'est son choix à la génération.
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, FIN))).estSucces());
        int fin2 = LicenseDates.jourEpoch(2028, 10, 18);
        assertTrue(ouvrirApplication().activer(
                code(LicensePayload.limitee(idTelephone, DEBUT, fin2, false))).estSucces());
        assertFalse(ouvrirApplication().peutUtiliserStock());
    }
}
