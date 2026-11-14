package com.example.gkm_bar_fer.sauvegarde;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.gkm_bar_fer.sauvegarde.MoteurRestauration.Resultat;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class MoteurRestaurationTest {

    private static final String ID_CLIENT = "6f1c2a52-9d1e-4e0b-8a43-0a9f6f1d2c11";

    /** Faux fichiers de préférences en mémoire. */
    private static class FauxMagasin implements MoteurRestauration.Magasin {
        Map<String, Map<String, Object>> donnees = new LinkedHashMap<>();
        int appels = 0;
        int echecAuAppel = -1;      // numéro de l'appel d'écriture qui échoue (écrit à moitié)

        @Override
        public Map<String, ? extends Map<String, ?>> instantane() {
            return donnees;
        }

        @Override
        public boolean ecrire(Map<String, Map<String, Object>> fichiers) {
            appels++;
            boolean echec = appels == echecAuAppel;
            for (Map.Entry<String, Map<String, Object>> e : fichiers.entrySet()) {
                if (e.getValue().isEmpty()) {
                    donnees.remove(e.getKey());
                } else {
                    donnees.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
                }
                if (echec) {
                    return false;   // s'arrête après le premier fichier
                }
            }
            return true;
        }
    }

    private static Map<String, Map<String, Object>> clients(String json, boolean avecStock) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put(NomsPrefs.CLE_CLIENTS, json);
        m.put(NomsPrefs.CLIENTS, c);
        if (avecStock) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put(NomsPrefs.CLE_STOCK, "{\"N12\":[1]}");
            s.put(NomsPrefs.CLE_VERSION, 3L);
            m.put(NomsPrefs.PREFIXE_STOCK_CLIENT + ID_CLIENT, s);
        }
        return m;
    }

    private static SauvegardeCodec.Contenu sauvegarde() throws Exception {
        byte[] fichier = SauvegardeCodec.ecrire(
                clients("[{\"id\":\"A\"},{\"id\":\"B\"}]", true), "1.0", 1793975520000L);
        return SauvegardeCodec.lire(fichier);
    }

    private static String empreinte(Map<String, ? extends Map<String, ?>> d) {
        return SauvegardeCodec.empreinte(d);
    }

    @Test
    public void installationVide_estRestauree() throws Exception {
        SauvegardeCodec.Contenu contenu = sauvegarde();
        FauxMagasin magasin = new FauxMagasin();

        assertEquals(Resultat.RESTAUREE, MoteurRestauration.restaurer(contenu, magasin));
        assertEquals(empreinte(contenu.prefs), empreinte(magasin.donnees));
    }

    @Test
    public void donneesExistantes_sontRemplacees_etLesFichiersEnTropVides() throws Exception {
        SauvegardeCodec.Contenu contenu = sauvegarde();
        FauxMagasin magasin = new FauxMagasin();
        magasin.donnees = clients("[{\"id\":\"Z\"}]", false);
        Map<String, Object> enTrop = new LinkedHashMap<>();
        enTrop.put(NomsPrefs.CLE_STOCK, "{\"N8\":[2]}");
        magasin.donnees.put(NomsPrefs.PREFIXE_STOCK_CLIENT + "aaaa-1111", enTrop);

        assertEquals(Resultat.RESTAUREE, MoteurRestauration.restaurer(contenu, magasin));
        assertFalse(magasin.donnees.containsKey(NomsPrefs.PREFIXE_STOCK_CLIENT + "aaaa-1111"));
        assertTrue(magasin.donnees.get(NomsPrefs.CLIENTS)
                .get(NomsPrefs.CLE_CLIENTS).toString().contains("\"B\""));
    }

    @Test
    public void echecPendantEcriture_remetLesDonneesD_avant() throws Exception {
        SauvegardeCodec.Contenu contenu = sauvegarde();
        FauxMagasin magasin = new FauxMagasin();
        magasin.donnees = clients("[{\"id\":\"Z\"}]", false);
        String avant = empreinte(magasin.donnees);
        magasin.echecAuAppel = 1;

        assertEquals(Resultat.ECHEC_ANNULEE, MoteurRestauration.restaurer(contenu, magasin));
        assertEquals(avant, empreinte(magasin.donnees));
    }

    @Test
    public void fichierAbime_estRefuseAvantToutEcriture() throws Exception {
        byte[] fichier = SauvegardeCodec.ecrire(clients("[{\"id\":\"A\"}]", true), "1.0", 1L);
        fichier[fichier.length / 2] ^= 0x55;
        try {
            SauvegardeCodec.lire(fichier);
            throw new AssertionError("Le fichier abîmé aurait dû être refusé");
        } catch (SauvegardeCodec.InvalideException attendu) {
            // OK : refusé par le codec, donc rien n'est écrit
        }
    }
}