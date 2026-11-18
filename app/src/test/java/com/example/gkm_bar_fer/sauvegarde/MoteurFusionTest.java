package com.example.gkm_bar_fer.sauvegarde;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class MoteurFusionTest {

    private static final String ID_A = "6f1c2a52-9d1e-4e0b-8a43-0a9f6f1d2c11";
    private static final String ID_B = "7a2b3c63-1e2f-4f1c-9b54-1b0a7a2e3d22";

    private static String client(String id, String nom, long date) {
        return "{\"id\":\"" + id + "\",\"nom\":\"" + nom + "\",\"ville\":\"\",\"date\":" + date + "}";
    }

    private static String calcul(String id, String clientId, String nom, long creation, long modif) {
        return "{\"id\":\"" + id + "\",\"clientId\":\"" + clientId + "\",\"nom\":\"" + nom
                + "\",\"dateCreation\":" + creation + ",\"dateModification\":" + modif
                + ",\"donnees\":\"{}\"}";
    }

    /** Une sauvegarde vérifiée (passe par le vrai codec : écriture puis lecture). */
    private static SauvegardeCodec.Contenu sauvegarde(long date, String clients, String calculs,
                                                      String stockGlobal, String stockClientA)
            throws Exception {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put(NomsPrefs.CLE_CLIENTS, clients);
        m.put(NomsPrefs.CLIENTS, c);
        if (calculs != null) {
            Map<String, Object> k = new LinkedHashMap<>();
            k.put(NomsPrefs.CLE_CALCULS, calculs);
            m.put(NomsPrefs.CALCULS, k);
        }
        if (stockGlobal != null) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put(NomsPrefs.CLE_STOCK, stockGlobal);
            g.put(NomsPrefs.CLE_VERSION, 1L);
            m.put(NomsPrefs.STOCK_GLOBAL, g);
        }
        if (stockClientA != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put(NomsPrefs.CLE_STOCK, stockClientA);
            s.put(NomsPrefs.CLE_VERSION, 2L);
            m.put(NomsPrefs.PREFIXE_STOCK_CLIENT + ID_A, s);
        }
        return SauvegardeCodec.lire(SauvegardeCodec.ecrire(m, "1.0", date));
    }

    private static JSONArray tableau(Map<String, Map<String, Object>> prefs, String fichier, String cle)
            throws Exception {
        return new JSONArray((String) prefs.get(fichier).get(cle));
    }

    private static String nom(JSONArray tableau, String id) throws Exception {
        for (int i = 0; i < tableau.length(); i++) {
            if (tableau.getJSONObject(i).getString("id").equals(id)) {
                return tableau.getJSONObject(i).getString("nom");
            }
        }
        return null;
    }

    private SauvegardeCodec.Contenu moisDernier() throws Exception {
        return sauvegarde(1000L,
                "[" + client(ID_A, "Ancien nom", 10) + "," + client(ID_B, "Client B", 20) + "]",
                "[" + calcul("c1", ID_A, "Calcul 1 ancien", 50, 100) + "]",
                "{\"N12\":[1]}", "{\"N12\":[11]}");
    }

    private SauvegardeCodec.Contenu semaineDerniere() throws Exception {
        return sauvegarde(2000L,
                "[" + client(ID_A, "Nom semaine", 10) + "]",
                "[" + calcul("c1", ID_A, "Calcul 1 modifie", 50, 200) + ","
                        + calcul("c2", ID_A, "Calcul 2", 150, 150) + "]",
                "{\"N12\":[2]}", "{\"N12\":[22]}");
    }

    private SauvegardeCodec.Contenu ilYa5Heures() throws Exception {
        return sauvegarde(3000L,
                "[" + client(ID_A, "Nom recent", 10) + "]",
                "[" + calcul("c1", ID_A, "Calcul 1 modifie", 50, 200) + "]",
                null, "{\"N12\":[33]}");
    }

    @Test
    public void clients_sontReunis_etLaVersionRecenteGagne() throws Exception {
        Map<String, Map<String, Object>> f = MoteurFusion.fusionner(
                Arrays.asList(moisDernier(), semaineDerniere(), ilYa5Heures()));
        JSONArray clients = tableau(f, NomsPrefs.CLIENTS, NomsPrefs.CLE_CLIENTS);

        assertEquals(2, clients.length());
        assertEquals("Nom recent", nom(clients, ID_A));
        assertEquals("Client B", nom(clients, ID_B));       // seulement dans la plus ancienne
    }

    @Test
    public void calculs_sontReunis_sansDoublon_dernierModifieGagne() throws Exception {
        Map<String, Map<String, Object>> f = MoteurFusion.fusionner(
                Arrays.asList(moisDernier(), semaineDerniere(), ilYa5Heures()));
        JSONArray calculs = tableau(f, NomsPrefs.CALCULS, NomsPrefs.CLE_CALCULS);

        assertEquals(2, calculs.length());
        assertEquals("c1", calculs.getJSONObject(0).getString("id"));   // plus récent en premier
        assertEquals("Calcul 1 modifie", calculs.getJSONObject(0).getString("nom"));
    }

    @Test
    public void stock_estPrisTelQuelDansLaSauvegardeLaPlusRecente() throws Exception {
        Map<String, Map<String, Object>> f = MoteurFusion.fusionner(
                Arrays.asList(moisDernier(), semaineDerniere(), ilYa5Heures()));

        assertEquals("{\"N12\":[33]}",
                f.get(NomsPrefs.PREFIXE_STOCK_CLIENT + ID_A).get(NomsPrefs.CLE_STOCK));
        // la plus récente n'a pas de stock global : on prend le plus récent qui en a un
        assertEquals("{\"N12\":[2]}", f.get(NomsPrefs.STOCK_GLOBAL).get(NomsPrefs.CLE_STOCK));
    }

    @Test
    public void ordreDeSelection_neChangePasLeResultat() throws Exception {
        SauvegardeCodec.Contenu a = moisDernier();
        SauvegardeCodec.Contenu b = semaineDerniere();
        SauvegardeCodec.Contenu c = ilYa5Heures();

        Map<String, Map<String, Object>> un = MoteurFusion.fusionner(Arrays.asList(a, b, c));
        Map<String, Map<String, Object>> deux = MoteurFusion.fusionner(Arrays.asList(c, a, b));

        assertEquals(SauvegardeCodec.empreinte(un), SauvegardeCodec.empreinte(deux));
    }

    @Test
    public void memeFichierDeuxFois_neCreeAucunDoublon() throws Exception {
        SauvegardeCodec.Contenu c = ilYa5Heures();
        Map<String, Map<String, Object>> f = MoteurFusion.fusionner(Arrays.asList(c, c));

        assertEquals(1, tableau(f, NomsPrefs.CLIENTS, NomsPrefs.CLE_CLIENTS).length());
        assertEquals(1, tableau(f, NomsPrefs.CALCULS, NomsPrefs.CLE_CALCULS).length());
    }

    @Test
    public void resultat_passeLaValidationDuCodec_etLApercuEstCoherent() throws Exception {
        Map<String, Map<String, Object>> f = MoteurFusion.fusionner(
                Arrays.asList(moisDernier(), semaineDerniere(), ilYa5Heures()));
        SauvegardeCodec.Contenu relu = SauvegardeCodec.lire(SauvegardeCodec.ecrire(f, "1.0", 3000L));

        assertEquals(2, relu.apercu.nbClients);
        assertEquals(2, relu.apercu.nbCalculs);
        assertEquals(1, relu.apercu.nbStocksClients);
        assertEquals(1, relu.apercu.nbTypesStock);
    }

    @Test
    public void clientSansId_refuseLaFusion() throws Exception {
        SauvegardeCodec.Contenu mauvais = sauvegarde(4000L, "[{\"nom\":\"sans id\"}]", null, null, null);
        try {
            MoteurFusion.fusionner(Arrays.asList(ilYa5Heures(), mauvais));
            throw new AssertionError("La fusion aurait dû être refusée");
        } catch (JSONException attendu) {
            assertTrue(true);
        }
    }

    @Test
    public void deuxCalculsIdentiques_maisIdsDifferents_sontTousLesDeuxGardes() throws Exception {
        // Même nom, mêmes données, mêmes dates : seuls les ids (UUID) diffèrent.
        SauvegardeCodec.Contenu a = sauvegarde(1000L,
                "[" + client(ID_A, "Client A", 10) + "]",
                "[" + calcul("id-1", ID_A, "Poteaux", 50, 100) + "]", null, null);
        SauvegardeCodec.Contenu b = sauvegarde(2000L,
                "[" + client(ID_A, "Client A", 10) + "]",
                "[" + calcul("id-1", ID_A, "Poteaux", 50, 100) + ","
                        + calcul("id-2", ID_A, "Poteaux", 50, 100) + "]", null, null);

        Map<String, Map<String, Object>> f = MoteurFusion.fusionner(Arrays.asList(a, b));
        // id-1 est le MÊME calcul vu dans 2 fichiers (compté une fois) ; id-2 est un autre calcul identique.
        assertEquals(2, tableau(f, NomsPrefs.CALCULS, NomsPrefs.CLE_CALCULS).length());
    }

    @Test
    public void deuxClientsAvecLeMemeStock_gardentChacunLeurStock() throws Exception {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put(NomsPrefs.CLE_CLIENTS, "[" + client(ID_A, "A", 10) + "," + client(ID_B, "B", 20) + "]");
        m.put(NomsPrefs.CLIENTS, c);
        for (String id : new String[]{ID_A, ID_B}) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put(NomsPrefs.CLE_STOCK, "{\"N12\":[{\"longueur\":2.5,\"quantite\":4}]}");   // même état de chutes
            s.put(NomsPrefs.CLE_VERSION, 1L);
            m.put(NomsPrefs.PREFIXE_STOCK_CLIENT + id, s);
        }
        SauvegardeCodec.Contenu unique = SauvegardeCodec.lire(SauvegardeCodec.ecrire(m, "1.0", 1000L));
        SauvegardeCodec.Contenu autre = sauvegarde(2000L, "[" + client(ID_A, "A", 10) + "]", null, null, null);

        Map<String, Map<String, Object>> f = MoteurFusion.fusionner(Arrays.asList(unique, autre));
        assertTrue(f.containsKey(NomsPrefs.PREFIXE_STOCK_CLIENT + ID_A));
        assertTrue(f.containsKey(NomsPrefs.PREFIXE_STOCK_CLIENT + ID_B));
    }
}