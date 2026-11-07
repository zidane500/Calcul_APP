package com.example.gkm_bar_fer.sauvegarde;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.gkm_bar_fer.sauvegarde.SauvegardeCodec.Contenu;
import com.example.gkm_bar_fer.sauvegarde.SauvegardeCodec.InvalideException;
import com.example.gkm_bar_fer.sauvegarde.SauvegardeCodec.Motif;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SauvegardeCodecTest {

    private static final long DATE = 1793975520000L; // 2026-11-06 14:32 UTC
    private static final String ID_CLIENT = "6f1c2a52-9d1e-4e0b-8a43-0a9f6f1d2c11";

    // ------------------------------------------------------------------
    // Données d'exemple proches de celles de l'application
    // ------------------------------------------------------------------

    private static Map<String, Map<String, ?>> exemple() {
        Map<String, Object> clients = new HashMap<>();
        clients.put("clients_json",
                "[{\"id\":\"" + ID_CLIENT + "\",\"nom\":\"Ali Ben Salah\",\"ville\":\"Nabeul\"}]");

        Map<String, Object> calculs = new HashMap<>();
        calculs.put("calculs_json", "[{\"id\":\"k1\",\"nom\":\"Calcul 1\"},{\"id\":\"k2\",\"nom\":\"Calcul 2\"}]");

        Map<String, Object> stock = new HashMap<>();
        stock.put("stock_json", "{\"HA12\":[{\"longueur\":2.5,\"quantite\":3}],\"HA10\":[{\"longueur\":4.0,\"quantite\":1}]}");
        stock.put("stock_version", 7L);
        stock.put("historique_json", "[{\"date\":1,\"action\":\"ajout\"}]");
        stock.put("stock_operation_abc", "{\"etat\":\"confirmee\",\"versionApres\":7}");

        Map<String, Object> stockClient = new HashMap<>();
        stockClient.put("stock_json", "{\"HA8\":[{\"longueur\":1.2,\"quantite\":5}]}");
        stockClient.put("stock_version", 2L);

        Map<String, Map<String, ?>> prefs = new LinkedHashMap<>();
        prefs.put("clients_prefs", clients);
        prefs.put("calculs_clients_prefs", calculs);
        prefs.put("stock_dechets_prefs", stock);
        prefs.put("stock_client_" + ID_CLIENT, stockClient);
        return prefs;
    }

    // ------------------------------------------------------------------
    // Fabrication de fichiers (valides ou piégés) pour les tests
    // ------------------------------------------------------------------

    private static byte[] zip(Map<String, byte[]> entrees) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(sortie)) {
            for (Map.Entry<String, byte[]> e : entrees.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return sortie.toByteArray();
    }

    private static byte[] manifest(byte[] donnees, String format, int version, String somme)
            throws Exception {
        JSONObject m = new JSONObject();
        m.put("format", format);
        m.put("formatVersion", version);
        m.put("versionApp", "1.0.0");
        m.put("dateMs", DATE);
        m.put("sha256", somme != null ? somme : SauvegardeCodec.sha256Hex(donnees));
        return m.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fichier(String donneesJson, String format, int version, String somme)
            throws Exception {
        byte[] donnees = donneesJson.getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> entrees = new LinkedHashMap<>();
        entrees.put("manifest.json", manifest(donnees, format, version, somme));
        entrees.put("donnees.json", donnees);
        return zip(entrees);
    }

    private static byte[] fichierValide(String donneesJson) throws Exception {
        return fichier(donneesJson, "FERPRO_SAUVEGARDE", 1, null);
    }

    private static final String DONNEES_OK =
            "{\"prefs\":{\"clients_prefs\":{\"clients_json\":{\"t\":\"s\",\"v\":\"[]\"}}}}";

    private static void assertRefuse(Motif attendu, byte[] octets) {
        try {
            SauvegardeCodec.lire(octets);
            fail("Le fichier aurait dû être refusé (" + attendu + ")");
        } catch (InvalideException e) {
            assertEquals(attendu, e.motif);
        }
    }

    // ------------------------------------------------------------------
    // Aller-retour
    // ------------------------------------------------------------------

    @Test
    public void allerRetour_conserveExactementLesDonnees() throws Exception {
        Map<String, Map<String, ?>> prefs = exemple();

        byte[] octets = SauvegardeCodec.ecrire(prefs, "1.0.0", DATE);
        Contenu contenu = SauvegardeCodec.lire(octets);

        assertEquals(prefs.keySet(), contenu.prefs.keySet());
        for (String nom : prefs.keySet()) {
            assertEquals("Fichier " + nom, prefs.get(nom), contenu.prefs.get(nom));
        }
    }

    @Test
    public void allerRetour_conserveTousLesTypesDeValeurs() throws Exception {
        Map<String, Object> stock = new HashMap<>();
        stock.put("texte", "\u00d812 \u00ab \u00e9 \u00e0 \u0663 \u00bb");
        stock.put("long", 9_000_000_000L);
        stock.put("entier", -42);
        stock.put("booleen", true);
        stock.put("decimal", 1.1f);
        stock.put("ensemble", new HashSet<>(Arrays.asList("a", "b", "c")));
        Map<String, Map<String, ?>> prefs = new LinkedHashMap<>();
        prefs.put("stock_dechets_prefs", stock);

        Contenu contenu = SauvegardeCodec.lire(SauvegardeCodec.ecrire(prefs, "1.0.0", DATE));

        assertEquals(stock, contenu.prefs.get("stock_dechets_prefs"));
        assertEquals(Long.class, contenu.prefs.get("stock_dechets_prefs").get("long").getClass());
        assertEquals(Integer.class, contenu.prefs.get("stock_dechets_prefs").get("entier").getClass());
    }

    @Test
    public void allerRetour_viaUnFlux() throws Exception {
        byte[] octets = SauvegardeCodec.ecrire(exemple(), "1.0.0", DATE);
        Contenu contenu = SauvegardeCodec.lire(new ByteArrayInputStream(octets));
        assertEquals(4, contenu.prefs.size());
    }

    @Test
    public void sauvegardeVide_estValide() throws Exception {
        Contenu contenu = SauvegardeCodec.lire(
                SauvegardeCodec.ecrire(new HashMap<String, Map<String, ?>>(), "1.0.0", DATE));
        assertTrue(contenu.prefs.isEmpty());
        assertEquals(0, contenu.apercu.nbClients);
    }

    @Test
    public void apercu_compteLesElements() throws Exception {
        Contenu contenu = SauvegardeCodec.lire(SauvegardeCodec.ecrire(exemple(), "1.2.3", DATE));

        assertEquals(1, contenu.apercu.nbClients);
        assertEquals(2, contenu.apercu.nbCalculs);
        assertEquals(2, contenu.apercu.nbTypesStock);
        assertEquals(1, contenu.apercu.nbStocksClients);
        assertEquals("1.2.3", contenu.apercu.versionApp);
        assertEquals(DATE, contenu.apercu.dateMs);
        assertEquals(1, contenu.apercu.versionFormat);
    }

    // ------------------------------------------------------------------
    // Écriture : ce qui ne doit jamais être sauvegardé
    // ------------------------------------------------------------------

    @Test
    public void ecriture_refuseLaLicence() {
        Map<String, Object> licence = new HashMap<>();
        licence.put("installation_id", "FP-A7K2-98MX-42PQ");
        Map<String, Map<String, ?>> prefs = new LinkedHashMap<>();
        prefs.put("license_prefs", licence);

        try {
            SauvegardeCodec.ecrire(prefs, "1.0.0", DATE);
            fail("La licence ne doit jamais être sauvegardée");
        } catch (IllegalArgumentException attendu) {
            // OK
        }
    }

    @Test
    public void ecriture_refuseUnNomDeStockClientDangereux() {
        for (String nom : new String[]{"stock_client_../x", "stock_client_", "stock_client_a/b",
                "stock_client_a.b", "autre_prefs", ""}) {
            Map<String, Map<String, ?>> prefs = new LinkedHashMap<>();
            prefs.put(nom, new HashMap<String, Object>());
            try {
                SauvegardeCodec.ecrire(prefs, "1.0.0", DATE);
                fail("Nom accepté à tort : " + nom);
            } catch (IllegalArgumentException attendu) {
                // OK
            }
        }
    }

    // ------------------------------------------------------------------
    // Lecture : fichiers refusés
    // ------------------------------------------------------------------

    @Test
    public void lecture_refuseUnFichierVide() {
        assertRefuse(Motif.FICHIER_ILLISIBLE, new byte[0]);
    }

    @Test
    public void lecture_refuseUnFichierQuelconque() {
        assertRefuse(Motif.FICHIER_ILLISIBLE,
                "Ceci n'est pas une sauvegarde".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void lecture_refuseUnZipTronque() throws Exception {
        byte[] octets = SauvegardeCodec.ecrire(exemple(), "1.0.0", DATE);
        byte[] coupe = Arrays.copyOf(octets, octets.length / 2);
        try {
            SauvegardeCodec.lire(coupe);
            fail("Un fichier tronqué doit être refusé");
        } catch (InvalideException e) {
            assertTrue(e.motif == Motif.FICHIER_ILLISIBLE || e.motif == Motif.SOMME_DE_CONTROLE);
        }
    }

    @Test
    public void lecture_refuseUnZipSansManifeste() throws Exception {
        Map<String, byte[]> entrees = new LinkedHashMap<>();
        entrees.put("donnees.json", DONNEES_OK.getBytes(StandardCharsets.UTF_8));
        assertRefuse(Motif.FICHIER_ILLISIBLE, zip(entrees));
    }

    @Test
    public void lecture_refuseUnAutreFormat() throws Exception {
        assertRefuse(Motif.FORMAT_INCONNU, fichier(DONNEES_OK, "AUTRE_APPLI", 1, null));
    }

    @Test
    public void lecture_refuseUneVersionPlusRecente() throws Exception {
        assertRefuse(Motif.VERSION_TROP_RECENTE,
                fichier(DONNEES_OK, "FERPRO_SAUVEGARDE", SauvegardeCodec.VERSION_FORMAT + 1, null));
    }

    @Test
    public void lecture_refuseUneVersionInvalide() throws Exception {
        assertRefuse(Motif.FORMAT_INCONNU, fichier(DONNEES_OK, "FERPRO_SAUVEGARDE", 0, null));
    }

    @Test
    public void lecture_detecteDesDonneesModifiees() throws Exception {
        byte[] originales = DONNEES_OK.getBytes(StandardCharsets.UTF_8);
        String modifiees = DONNEES_OK.replace("[]", "[{}]");

        Map<String, byte[]> entrees = new LinkedHashMap<>();
        entrees.put("manifest.json", manifest(originales, "FERPRO_SAUVEGARDE", 1, null));
        entrees.put("donnees.json", modifiees.getBytes(StandardCharsets.UTF_8));

        assertRefuse(Motif.SOMME_DE_CONTROLE, zip(entrees));
    }

    @Test
    public void lecture_refuseLaLicenceMemeAvecUneBonneSomme() throws Exception {
        String piege = "{\"prefs\":{\"license_prefs\":{\"license_code\":{\"t\":\"s\",\"v\":\"X\"}}}}";
        assertRefuse(Motif.CONTENU_INTERDIT, fichierValide(piege));
    }

    @Test
    public void lecture_refuseUnNomDeFichierDeDonneesDangereux() throws Exception {
        String piege = "{\"prefs\":{\"stock_client_../../x\":{\"stock_json\":{\"t\":\"s\",\"v\":\"{}\"}}}}";
        assertRefuse(Motif.CONTENU_INTERDIT, fichierValide(piege));
    }

    @Test
    public void lecture_refuseUneEntreeInattendue() throws Exception {
        byte[] donnees = DONNEES_OK.getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> entrees = new LinkedHashMap<>();
        entrees.put("manifest.json", manifest(donnees, "FERPRO_SAUVEGARDE", 1, null));
        entrees.put("donnees.json", donnees);
        entrees.put("../evil.txt", "x".getBytes(StandardCharsets.UTF_8));
        assertRefuse(Motif.CONTENU_INTERDIT, zip(entrees));
    }

    @Test
    public void lecture_refuseUnJsonInvalideDansUneCleConnue() throws Exception {
        String piege = "{\"prefs\":{\"clients_prefs\":{\"clients_json\":{\"t\":\"s\",\"v\":\"pas du json\"}}}}";
        assertRefuse(Motif.CONTENU_INVALIDE, fichierValide(piege));
    }

    @Test
    public void lecture_refuseUneMauvaiseFormeDeJson() throws Exception {
        // clients_json doit être un tableau, pas un objet
        String piege = "{\"prefs\":{\"clients_prefs\":{\"clients_json\":{\"t\":\"s\",\"v\":\"{}\"}}}}";
        assertRefuse(Motif.CONTENU_INVALIDE, fichierValide(piege));
    }

    @Test
    public void lecture_refuseUnTypeInconnu() throws Exception {
        String piege = "{\"prefs\":{\"clients_prefs\":{\"x\":{\"t\":\"zzz\",\"v\":1}}}}";
        assertRefuse(Motif.CONTENU_INVALIDE, fichierValide(piege));
    }

    @Test
    public void lecture_refuseUneValeurDuMauvaisType() throws Exception {
        String piege = "{\"prefs\":{\"stock_dechets_prefs\":{\"stock_version\":{\"t\":\"l\",\"v\":\"pas un nombre\"}}}}";
        assertRefuse(Motif.CONTENU_INVALIDE, fichierValide(piege));
    }

    @Test
    public void lecture_refuseUnEntierHorsLimites() throws Exception {
        String piege = "{\"prefs\":{\"stock_dechets_prefs\":{\"n\":{\"t\":\"i\",\"v\":99999999999}}}}";
        assertRefuse(Motif.CONTENU_INVALIDE, fichierValide(piege));
    }

    @Test
    public void lecture_refuseDesDonneesQuiNeSontPasDuJson() throws Exception {
        assertRefuse(Motif.CONTENU_INVALIDE, fichierValide("ceci n'est pas du json"));
    }

    @Test
    public void lecture_refuseUneBombeZip() throws Exception {
        byte[] enorme = new byte[(int) SauvegardeCodec.MAX_OCTETS_ENTREE + 1];   // que des zéros
        Map<String, byte[]> entrees = new LinkedHashMap<>();
        entrees.put("manifest.json", manifest(enorme, "FERPRO_SAUVEGARDE", 1, null));
        entrees.put("donnees.json", enorme);

        byte[] octets = zip(entrees);
        assertTrue("Le piège doit être petit une fois compressé", octets.length < 1024 * 1024);
        assertRefuse(Motif.FICHIER_TROP_GRAND, octets);
    }

    @Test
    public void lecture_refuseUnFluxTropGrand() {
        byte[] trop = new byte[(int) SauvegardeCodec.MAX_OCTETS_FICHIER + 1];
        try {
            SauvegardeCodec.lire(new ByteArrayInputStream(trop));
            fail("Un fichier trop gros doit être refusé");
        } catch (InvalideException e) {
            assertEquals(Motif.FICHIER_TROP_GRAND, e.motif);
        }
    }

    // ------------------------------------------------------------------
    // Nom de fichier
    // ------------------------------------------------------------------

    @Test
    public void nomFichier_estLisibleEtSansCaracteresInterdits() {
        String nom = SauvegardeCodec.nomFichier(DATE, TimeZone.getTimeZone("UTC"));
        assertEquals("FerPro_Sauvegarde_2026-11-06_14h32.ferpro", nom);
        assertFalse(nom.contains(":"));
        assertFalse(nom.contains("/"));
    }

    @Test
    public void nomFichier_utiliseLeFuseauDuTelephone() {
        String nom = SauvegardeCodec.nomFichier(DATE, TimeZone.getTimeZone("Africa/Tunis"));
        assertEquals("FerPro_Sauvegarde_2026-11-06_15h32.ferpro", nom);   // Tunisie = UTC+1
    }

    // ------------------------------------------------------------------
    // Liste blanche
    // ------------------------------------------------------------------

    @Test
    public void listeBlanche_accepteLesDonneesMetier() {
        assertTrue(NomsPrefs.estAutorise("clients_prefs"));
        assertTrue(NomsPrefs.estAutorise("calculs_clients_prefs"));
        assertTrue(NomsPrefs.estAutorise("stock_dechets_prefs"));
        assertTrue(NomsPrefs.estAutorise("stock_client_" + ID_CLIENT));
    }

    @Test
    public void listeBlanche_refuseLaLicenceEtLeReste() {
        assertFalse(NomsPrefs.estAutorise("license_prefs"));
        assertFalse(NomsPrefs.estAutorise(null));
        assertFalse(NomsPrefs.estAutorise(""));
        assertFalse(NomsPrefs.estAutorise("stock_client_"));
        assertFalse(NomsPrefs.estAutorise("stock_client_../x"));
        assertFalse(NomsPrefs.estAutorise("androidx.appcompat.app.AppCompatDelegate"));
    }
}