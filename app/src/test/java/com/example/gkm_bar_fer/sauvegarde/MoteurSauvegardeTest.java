package com.example.gkm_bar_fer.sauvegarde;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.gkm_bar_fer.sauvegarde.MoteurSauvegarde.Resultat;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MoteurSauvegardeTest {

    private static final long T0 = 1793975520000L;      // 2026-11-06 14:32 UTC
    private static final long MINUTE = 60_000L;
    private static final String ID_CLIENT = "6f1c2a52-9d1e-4e0b-8a43-0a9f6f1d2c11";

    // ------------------------------------------------------------------
    // Faux dossier et fausse mémoire
    // ------------------------------------------------------------------

    private static class FauxDossier implements Destination {
        final Map<String, byte[]> fichiers = new TreeMap<>();   // nom -> octets
        boolean echecEcriture = false;
        boolean corromprePendantEcriture = false;

        @Override
        public List<Entree> lister() {
            List<Entree> resultat = new ArrayList<>();
            for (String nom : fichiers.keySet()) {
                resultat.add(new Entree(nom, nom));
            }
            Collections.reverse(resultat);                     // le plus récent d'abord
            return resultat;
        }

        @Override
        public Entree creer(String nom, byte[] octets) throws IOException {
            if (echecEcriture) {
                throw new IOException("disque plein");
            }
            byte[] copie = Arrays.copyOf(octets, octets.length);
            if (corromprePendantEcriture) {
                copie[copie.length / 2] ^= 0x5A;               // un octet abîmé
            }
            fichiers.put(nom, copie);
            return new Entree(nom, nom);
        }

        @Override
        public InputStream ouvrir(Entree entree) {
            return new ByteArrayInputStream(fichiers.get(entree.nom));
        }

        @Override
        public void supprimer(Entree entree) {
            fichiers.remove(entree.nom);
        }
    }

    private static class FausseMemoire implements MoteurSauvegarde.Memoire {
        String empreinte;
        String dernierNom;
        long derniereDate;
        final Set<String> crees = new HashSet<>();

        @Override
        public String empreinte() {
            return empreinte;
        }

        @Override
        public String dernierNom() {
            return dernierNom;
        }

        @Override
        public long derniereDate() {
            return derniereDate;
        }

        @Override
        public void enregistrer(String empreinte, String nom, long dateMs) {
            this.empreinte = empreinte;
            this.dernierNom = nom;
            this.derniereDate = dateMs;
            crees.add(nom);
        }

        @Override
        public boolean aCree(String nom) {
            return crees.contains(nom);
        }

        @Override
        public void oublier(String nom) {
            crees.remove(nom);
        }
    }

    private FauxDossier dossier;
    private FausseMemoire memoire;
    private MoteurSauvegarde moteur;

    @Before
    public void preparer() {
        dossier = new FauxDossier();
        memoire = new FausseMemoire();
        moteur = new MoteurSauvegarde(dossier, memoire);
    }

    // ------------------------------------------------------------------
    // Données d'exemple
    // ------------------------------------------------------------------

    private static Map<String, Map<String, ?>> donnees(int nbCalculs) {
        StringBuilder calculs = new StringBuilder("[");
        for (int i = 0; i < nbCalculs; i++) {
            calculs.append(i > 0 ? "," : "").append("{\"id\":\"k").append(i).append("\"}");
        }
        calculs.append("]");

        Map<String, Object> clients = new HashMap<>();
        clients.put("clients_json", "[{\"id\":\"" + ID_CLIENT + "\",\"nom\":\"Ali\"}]");
        Map<String, Object> calculsPrefs = new HashMap<>();
        calculsPrefs.put("calculs_json", calculs.toString());
        Map<String, Object> stock = new HashMap<>();
        stock.put("stock_json", "{\"HA12\":[{\"longueur\":2.5,\"quantite\":3}]}");
        stock.put("stock_version", 4L);

        Map<String, Map<String, ?>> prefs = new LinkedHashMap<>();
        prefs.put("clients_prefs", clients);
        prefs.put("calculs_clients_prefs", calculsPrefs);
        prefs.put("stock_dechets_prefs", stock);
        return prefs;
    }

    private static Map<String, Map<String, ?>> donneesVides() {
        Map<String, Object> clients = new HashMap<>();
        clients.put("clients_json", "[]");
        Map<String, Object> calculs = new HashMap<>();
        calculs.put("calculs_json", "[]");
        Map<String, Object> stock = new HashMap<>();
        stock.put("stock_json", "{}");
        Map<String, Map<String, ?>> prefs = new LinkedHashMap<>();
        prefs.put("clients_prefs", clients);
        prefs.put("calculs_clients_prefs", calculs);
        prefs.put("stock_dechets_prefs", stock);
        return prefs;
    }

    private Resultat sauvegarder(Map<String, Map<String, ?>> prefs, long maintenant) {
        return moteur.executer(prefs, "1.0.0", maintenant);
    }

    // ------------------------------------------------------------------
    // Comportement du moteur
    // ------------------------------------------------------------------

    @Test
    public void premiereSauvegarde_estEcriteEtRelisible() throws Exception {
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0));

        assertEquals(1, dossier.fichiers.size());
        String nom = SauvegardeCodec.nomFichier(T0);
        assertTrue(dossier.fichiers.containsKey(nom));
        assertTrue(SauvegardeRegles.estNomSauvegarde(nom));

        SauvegardeCodec.Contenu relu = SauvegardeCodec.lire(dossier.fichiers.get(nom));
        assertEquals(1, relu.apercu.nbClients);
        assertEquals(2, relu.apercu.nbCalculs);
        assertEquals(nom, memoire.dernierNom());
    }

    @Test
    public void donneesIdentiques_neRecreentPasDeFichier() {
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0));
        assertEquals(Resultat.INCHANGEE, sauvegarder(donnees(2), T0 + 5 * MINUTE));
        assertEquals(Resultat.INCHANGEE, sauvegarder(donnees(2), T0 + 9 * MINUTE));
        assertEquals(1, dossier.fichiers.size());
    }

    @Test
    public void memeMinute_laSauvegardeRecenteRemplaceLAncienne() throws Exception {
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0));
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(3), T0 + 20_000L));   // même minute

        assertEquals(1, dossier.fichiers.size());
        SauvegardeCodec.Contenu relu = SauvegardeCodec.lire(
                dossier.fichiers.get(SauvegardeCodec.nomFichier(T0)));
        assertEquals(3, relu.apercu.nbCalculs);
    }

    @Test
    public void memeNomQuUnFichierEtranger_neLEcrasePas() {
        String nomEtranger = SauvegardeCodec.nomFichier(T0);
        dossier.fichiers.put(nomEtranger, new byte[]{9});

        assertEquals(Resultat.ECHEC, sauvegarder(donnees(2), T0));
        assertEquals(1, dossier.fichiers.get(nomEtranger).length);          // intact
    }

    @Test
    public void donneesModifiees_creentUneNouvelleVersion() {
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0));
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(3), T0 + 2 * MINUTE));
        assertEquals(2, dossier.fichiers.size());
    }

    @Test
    public void fichierSupprimeParLeClient_estRecree() {
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0));
        dossier.fichiers.clear();                                   // le client vide le dossier
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0 + 5 * MINUTE));
        assertEquals(1, dossier.fichiers.size());
    }

    @Test
    public void donneesVides_neSontJamaisEcrites() {
        assertEquals(Resultat.VIDE, sauvegarder(new HashMap<String, Map<String, ?>>(), T0));
        assertEquals(Resultat.VIDE, sauvegarder(donneesVides(), T0 + MINUTE));
        assertTrue(dossier.fichiers.isEmpty());
    }

    @Test
    public void unEtatVide_neRemplaceJamaisLaDerniereBonneSauvegarde() {
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0));
        String avant = memoire.dernierNom();

        assertEquals(Resultat.VIDE, sauvegarder(donneesVides(), T0 + 5 * MINUTE));

        assertEquals(1, dossier.fichiers.size());
        assertTrue(dossier.fichiers.containsKey(avant));
    }

    @Test
    public void rotation_ne_garde_que_les_cinq_dernieres_versions() {
        for (int i = 1; i <= 8; i++) {
            assertEquals(Resultat.REUSSIE, sauvegarder(donnees(i), T0 + i * MINUTE));
        }
        assertEquals(SauvegardeRegles.NB_VERSIONS_GARDEES, dossier.fichiers.size());

        // Ce sont les 5 plus récentes (i = 4 à 8)
        for (int i = 4; i <= 8; i++) {
            assertTrue(dossier.fichiers.containsKey(SauvegardeCodec.nomFichier(T0 + i * MINUTE)));
        }
        assertFalse(dossier.fichiers.containsKey(SauvegardeCodec.nomFichier(T0 + 3 * MINUTE)));
    }

    @Test
    public void rotation_ne_supprime_jamais_les_fichiers_d_une_installation_precedente() {
        // Fichiers laissés par l'installation d'avant la désinstallation : cette installation
        // ne les a pas créés, elle ne doit jamais les supprimer.
        String ancien1 = "FerPro_Sauvegarde_2026-10-01_09h00.ferpro";
        String ancien2 = "FerPro_Sauvegarde_2026-10-02_09h00.ferpro";
        dossier.fichiers.put(ancien1, new byte[]{1});
        dossier.fichiers.put(ancien2, new byte[]{2});

        for (int i = 1; i <= 9; i++) {
            sauvegarder(donnees(i), T0 + i * MINUTE);
        }

        assertTrue(dossier.fichiers.containsKey(ancien1));
        assertTrue(dossier.fichiers.containsKey(ancien2));
        assertEquals(SauvegardeRegles.NB_VERSIONS_GARDEES + 2, dossier.fichiers.size());
    }

    @Test
    public void ecritureImpossible_donneEchec_sansException() {
        dossier.echecEcriture = true;
        assertEquals(Resultat.ECHEC, sauvegarder(donnees(2), T0));
        assertTrue(dossier.fichiers.isEmpty());
        assertEquals(null, memoire.empreinte());
    }

    @Test
    public void fichierAbime_estSupprimeEtNEstPasMemorise() {
        dossier.corromprePendantEcriture = true;
        assertEquals(Resultat.ECHEC, sauvegarder(donnees(2), T0));

        assertTrue("Le fichier défectueux doit être supprimé", dossier.fichiers.isEmpty());
        assertEquals(null, memoire.empreinte());

        // Une fois le problème réglé, la sauvegarde suivante réussit.
        dossier.corromprePendantEcriture = false;
        assertEquals(Resultat.REUSSIE, sauvegarder(donnees(2), T0 + 2 * MINUTE));
    }

    @Test
    public void licenceDansLesDonnees_donneEchec_etRienNEstEcrit() {
        Map<String, Map<String, ?>> prefs = donnees(2);
        Map<String, Object> licence = new HashMap<>();
        licence.put("license_code", "X");
        prefs.put("license_prefs", licence);

        assertEquals(Resultat.ECHEC, sauvegarder(prefs, T0));
        assertTrue(dossier.fichiers.isEmpty());
    }

    // ------------------------------------------------------------------
    // Empreinte
    // ------------------------------------------------------------------

    @Test
    public void empreinte_nedependPasDeLOrdre() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("stock_json", "{}");
        a.put("stock_version", 1L);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("stock_version", 1L);
        b.put("stock_json", "{}");

        Map<String, Map<String, ?>> p1 = new LinkedHashMap<>();
        p1.put("stock_dechets_prefs", a);
        p1.put("clients_prefs", new HashMap<String, Object>());
        Map<String, Map<String, ?>> p2 = new LinkedHashMap<>();
        p2.put("clients_prefs", new HashMap<String, Object>());
        p2.put("stock_dechets_prefs", b);

        assertEquals(SauvegardeCodec.empreinte(p1), SauvegardeCodec.empreinte(p2));
    }

    @Test
    public void empreinte_changeQuandUneValeurChange() {
        assertNotEquals(SauvegardeCodec.empreinte(donnees(2)), SauvegardeCodec.empreinte(donnees(3)));
    }

    // ------------------------------------------------------------------
    // Règles
    // ------------------------------------------------------------------

    @Test
    public void delai_regroupeLesModificationsRapprochees() {
        // Aucune écriture récente : on attend juste le délai de regroupement.
        assertEquals(SauvegardeRegles.DELAI_REGROUPEMENT_MS, SauvegardeRegles.delai(T0, 0L));
    }

    @Test
    public void delai_respecteLEcartMinimalEntreDeuxFichiers() {
        long ecriture = T0;
        long maintenant = T0 + 10_000L;     // 10 s plus tard
        long attendu = SauvegardeRegles.INTERVALLE_MIN_MS - 10_000L;
        assertEquals(attendu, SauvegardeRegles.delai(maintenant, ecriture));
    }

    @Test
    public void delai_neDevientJamaisInferieurAuRegroupement() {
        assertEquals(SauvegardeRegles.DELAI_REGROUPEMENT_MS,
                SauvegardeRegles.delai(T0 + 59_000L, T0));   // il ne reste que 1 s d'écart
    }

    @Test
    public void nomsDeFichiers_reconnusEtRefuses() {
        assertTrue(SauvegardeRegles.estNomSauvegarde("FerPro_Sauvegarde_2026-11-06_14h32.ferpro"));
        assertTrue(SauvegardeRegles.estNomSauvegarde("FerPro_Sauvegarde_2026-11-06_14h32 (1).ferpro"));
        assertFalse(SauvegardeRegles.estNomSauvegarde("FerPro_Sauvegarde_2026-11-06_14h32.ferpro.tmp"));
        assertFalse(SauvegardeRegles.estNomSauvegarde("photo.jpg"));
        assertFalse(SauvegardeRegles.estNomSauvegarde(null));
    }

    @Test
    public void aSupprimer_gardeLesPlusRecents() {
        List<String> noms = Arrays.asList("e", "d", "c", "b", "a");   // du plus récent au plus ancien
        assertEquals(Arrays.asList("b", "a"), SauvegardeRegles.aSupprimer(noms, 3));
        assertTrue(SauvegardeRegles.aSupprimer(noms, 5).isEmpty());
        assertTrue(SauvegardeRegles.aSupprimer(noms, 9).isEmpty());
    }

    @Test
    public void apercuVide_estDetecte() throws Exception {
        SauvegardeCodec.Contenu vide = SauvegardeCodec.lire(
                SauvegardeCodec.ecrire(donneesVides(), "1.0.0", T0));
        assertNotNull(vide.apercu);
        assertTrue(SauvegardeRegles.estVide(vide.apercu));

        SauvegardeCodec.Contenu plein = SauvegardeCodec.lire(
                SauvegardeCodec.ecrire(donnees(1), "1.0.0", T0));
        assertFalse(SauvegardeRegles.estVide(plein.apercu));
    }
}