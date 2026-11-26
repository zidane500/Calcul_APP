package com.example.gkm_bar_fer.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests du lecteur de plans. Les valeurs des fiches de poutres (pages 6 à 12 du dossier « Ksar Ouled
 * Dabeb ») et des tableaux du plan de l'école sont celles imprimées sur les plans.
 */
public class AnalysePlanTest {

    private static ElementDetecte unique(List<String> lignes) {
        AnalysePlan.ResultatAnalyse r = AnalysePlan.analyser(lignes, 1);
        assertEquals(1, r.elements.size());
        return r.elements.get(0);
    }

    private static List<String> fiche(String titre, String[] rangees, String[] totaux, String acier) {
        List<String> l = new ArrayList<>();
        l.add("Arche Poutre BAEL Version 17.1");
        l.add(titre);
        l.add(acier);
        l.addAll(Arrays.asList(rangees));
        l.addAll(Arrays.asList(totaux));
        return l;
    }

    // ---------------- Fiches de poutres : toutes doivent passer le contrôle ----------------

    @Test
    public void toutesLesFichesDuPlanReel_passentLeControle() {
        Object[][] fiches = {
                {"Poutre n018 Niveau n01 A1 A A2", new String[]{"1 3HA10 278", "2 3HA10 91", "3 3HA10 308",
                        "4 15D×8 82", "5 15D×8 51", "6 3HA14 413", "7 3HA10 435", "8 24D×8 110", "9 24D×8 79"},
                        new String[]{"HA10 33,3/20.6", "HA14 12,4/15.0", "Dx8 65,2/25,8"}, "Acier=61.3 kg"},
                {"Poutre n019 Niveau n01 A3", new String[]{"1 3HA14 388", "2 3HA10 372", "3 20D×8 100", "4 20D×8 69"},
                        new String[]{"HA10 11,2/6.9", "HA14 11,6/14.1", "Dx8 33,8/13.3"}, "Acier=34.3 kg"},
                {"Poutre n020 Niveau n01 A4", new String[]{"1 3HA12 421", "2 3HA10 255", "3 3HA10 419",
                        "4 24D×8 110", "5 24D×8 79"},
                        new String[]{"HA10 20,2/12.5", "HA12 12,6/11.2", "Dx8 45,3/17.9"}, "Acier=41.6 kg"},
                {"Poutre n022 Niveau n01 A5", new String[]{"1 3HA14 565", "2 3HA10 549", "3 32D×8 120", "4 32D×8 89"},
                        new String[]{"HA10 16,5/10.2", "HA14 16,9/20.5", "Dx8 66,8/26.4"}, "Acier=57.0 kg"},
                {"Poutre n023 Niveau n01 A6", new String[]{"1 3HA14 485", "2 3HA10 469", "3 27D×8 120", "4 27D×8 89"},
                        new String[]{"HA10 14,1/8.7", "HA14 14,5/17.6", "Dx8 56,4/22.3"}, "Acier=48.5 kg"},
                {"Poutre n024 Niveau n01 A7", new String[]{"1 3HA10 469", "2 3HA10 469", "3 27D×8 120", "4 27D×8 89"},
                        new String[]{"HA10 28,1/17.4", "Dx8 56,4/22.3"}, "Acier=39.6 kg"},
                {"Poutre n025 Niveau n01 A8", new String[]{"1 3HA14 538", "2 3HA14 363", "3 3HA10 522",
                        "4 30D×8 120", "5 30D×8 89"},
                        new String[]{"HA10 15,7/9.7", "HA14 27,0/32.6", "Dx8 62,6/24.7"}, "Acier=67.0 kg"},
        };
        for (Object[] f : fiches) {
            ElementDetecte e = unique(fiche((String) f[0], (String[]) f[1], (String[]) f[2], (String) f[3]));
            assertEquals((String) f[0], ElementDetecte.Confiance.HAUTE, e.confiance);
            assertFalse((String) f[0], e.aUneErreur());
            assertEquals(TypeElementScan.POUTRE, e.type);
        }
    }

    @Test
    public void fichePoutre18_lecturesEtGroupes() {
        ElementDetecte e = unique(fiche("Poutre n018 Niveau n01 A1 A A2",
                new String[]{"1 3HA10 278", "2 3HA10 91", "3 3HA10 308", "4 15D×8 82", "5 15D×8 51",
                        "6 3HA14 413", "7 3HA10 435", "8 24D×8 110", "9 24D×8 79"},
                new String[]{"HA10 33,3/20.6", "HA14 12,4/15.0", "Dx8 65,2/25,8"}, "Acier=61.3 kg"));
        assertEquals("Poutre n°18 (A1 à A2)", e.repere);
        assertEquals(Integer.valueOf(2), e.nombre);
        assertEquals(9, e.barres.size());
        // 3HA10 278 / 91 / 308 / 435 + 3HA14 413 + 5 lignes de cadres (longueurs 82, 51, 110, 79) → 9 longueurs distinctes
        assertEquals(9, e.versGroupes().size());
    }

    @Test
    public void ocrReelDeLaPage6_incomplet_estRefuseAvecLeBonDiagnostic() {
        // Texte réellement renvoyé par l'OCR sur la page 6 : une ligne manque et les cadres ne sont pas lus.
        ElementDetecte e = unique(Arrays.asList(
                "Arche Poutre BAEL Version 17.1 Poutre nOl8S Niveau nol Al A Ae Béton=0.42 m3 Eb=2.5 cm 1",
                "Acier=61.3 kg d=153.8 kg/m3 | Eh=2.5 cm a",
                "1 3HA10 278 ©", "ps2 4, sey 3 | 3HAt10 308 Zz.", "3HA14 413 ©>", "A B 7 | 3HALO 435 ©",
                "HA10 33,3/20.6", "HAL4 12,4/15.0", "Dx8 65.2/25,8"));
        assertEquals("Poutre n°18", e.repere);
        assertEquals(ElementDetecte.Confiance.BASSE, e.confiance);
        assertTrue(e.aUneErreur());
        // « 3HAt10 » et « 3HALO » sont bien lus comme 3 barres de diamètre 10.
        assertEquals(4, e.barres.size());
        assertEquals(10, e.barres.get(1).diametre);
        assertEquals(10, e.barres.get(3).diametre);
        boolean manque = false;
        for (Alerte a : e.alertes) {
            if (a.niveau == Alerte.Niveau.ERREUR && a.message.contains("il manque")) {
                manque = true;
            }
        }
        assertTrue(manque);
    }

    @Test
    public void unChiffreFaux_estDetecteParLeControle() {
        // 3HA14 565 lu 3HA14 365 : le total du plan (16,9 m) ne correspond plus.
        ElementDetecte e = unique(fiche("Poutre n022 Niveau n01 A5",
                new String[]{"1 3HA14 365", "2 3HA10 549", "3 32D×8 120", "4 32D×8 89"},
                new String[]{"HA10 16,5/10.2", "HA14 16,9/20.5", "Dx8 66,8/26.4"}, "Acier=57.0 kg"));
        assertEquals(ElementDetecte.Confiance.BASSE, e.confiance);
        assertTrue(e.aUneErreur());
    }

    @Test
    public void sansTotauxLisibles_pasDeConfianceHaute() {
        ElementDetecte e = unique(Arrays.asList("Arche Poutre BAEL Version 17.1", "Poutre n019 Niveau n01",
                "1 3HA14 388", "2 3HA10 372"));
        assertEquals(ElementDetecte.Confiance.MOYENNE, e.confiance);
    }

    // ---------------- Tableaux du plan de l'école ----------------

    @Test
    public void tableauPoteaux_P1_et_P2() {
        AnalysePlan.ResultatAnalyse r = AnalysePlan.analyser(Arrays.asList("TABLEAU DES POTEAUX",
                "P1 (22x22) 4T12 cad Ø6/15cm", "P2 (Ø20) 5T10 cerce Ø6/15cm"), 1);
        assertEquals(2, r.elements.size());
        ElementDetecte p1 = r.elements.get(0);
        assertEquals("P1", p1.repere);
        assertEquals(TypeElementScan.POTEAU, p1.type);
        assertEquals(1, p1.barres.size());
        assertEquals(4, p1.barres.get(0).quantite);
        assertEquals(12, p1.barres.get(0).diametre);
        assertNull(p1.nombre);
        assertNull(p1.barres.get(0).longueurM);
        assertEquals(5, r.elements.get(1).barres.get(0).quantite);
        assertEquals(10, r.elements.get(1).barres.get(0).diametre);
    }

    @Test
    public void tableauFondations_grilleEtDeuxSens() {
        AnalysePlan.ResultatAnalyse r = AnalysePlan.analyser(Arrays.asList(
                "S1 120 x 120 x H 70 x 70 x 30 #7T12",
                "SJ1 160 x 120 x H 110 x 70 x 30 11T8/7T8 11T12/7T12"), 1);
        assertEquals(2, r.elements.size());
        ElementDetecte s1 = r.elements.get(0);
        assertEquals(TypeElementScan.SEMELLE, s1.type);
        assertEquals(14, s1.barres.get(0).quantite); // 7 par sens, doublé, signalé
        ElementDetecte sj1 = r.elements.get(1);
        assertEquals(4, sj1.barres.size());
        assertEquals(4, sj1.versGroupes().size()); // un groupe par jeu et par sens
    }

    @Test
    public void armaturesAuMetreEtCadres_ne_sontPasImportees() {
        ElementDetecte e = unique(Arrays.asList("CH1 (45x25)", "4HA10", "4HA12", "Cad+ET Ø 6", "e = 15cm",
                "5HA12/m"));
        assertEquals(2, e.barres.size());
    }

    @Test
    public void diametreImpossible_estIgnore() {
        AnalysePlan.ResultatAnalyse r = AnalysePlan.analyser(Arrays.asList("P1 4T13"), 1);
        assertEquals(0, r.elements.size());
    }

    @Test
    public void memeRepereMemesArmatures_gardeUneFois_etConflitSignale() {
        List<ElementDetecte> tous = new ArrayList<>();
        tous.addAll(AnalysePlan.analyser(Arrays.asList("P1 4T12"), 1).elements);
        tous.addAll(AnalysePlan.analyser(Arrays.asList("P1 4T12"), 2).elements);
        assertEquals(1, AnalysePlan.fusionner(tous).size());

        tous.addAll(AnalysePlan.analyser(Arrays.asList("P1 6T12"), 3).elements);
        List<ElementDetecte> f = AnalysePlan.fusionner(tous);
        assertEquals(2, f.size());
        assertTrue(f.get(0).aUneErreur() && f.get(1).aUneErreur());
    }

    @Test
    public void scorer_prefereLeBonSensDeLecture() {
        int bon = AnalysePlan.scorer(Arrays.asList("1 3HA10 278", "Acier=34.3 kg", "HA10 11,2/6.9"));
        int mauvais = AnalysePlan.scorer(Arrays.asList("0Ah3 872", "xx", "kg=3.43"));
        assertTrue(bon > mauvais);
    }

    // ---------------- Contrôle avant import et export ----------------

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("N8", "N10", "N12", "N14"));

    private static ControleImport.GroupeSaisi groupe(String nombre, String longueur, String... barres) {
        ControleImport.GroupeSaisi g = new ControleImport.GroupeSaisi();
        g.type = TypeElementScan.POTEAU;
        g.nombre = nombre;
        g.longueur = longueur;
        for (int i = 0; i + 1 < barres.length; i += 2) {
            g.barres.add(new ControleImport.LigneBarre(barres[i], barres[i + 1]));
        }
        return g;
    }

    @Test
    public void controleImport_refuseLesValeursVidesOuInvalides() {
        assertTrue(ControleImport.problemes(groupe("4", "3,00", "N12", "4"), TYPES).isEmpty());
        assertTrue(ControleImport.problemes(groupe("", "3", "N12", "4"), TYPES)
                .contains(ControleImport.Probleme.NOMBRE_INVALIDE));
        assertTrue(ControleImport.problemes(groupe("4", "", "N12", "4"), TYPES)
                .contains(ControleImport.Probleme.LONGUEUR_INVALIDE));
        assertTrue(ControleImport.problemes(groupe("4", "3", "N25", "4"), TYPES)
                .contains(ControleImport.Probleme.BARRE_INCONNUE));
        assertTrue(ControleImport.problemes(groupe("4", "3", "N12", "0"), TYPES)
                .contains(ControleImport.Probleme.QUANTITE_INVALIDE));
        assertTrue(ControleImport.problemes(groupe("4", "3"), TYPES)
                .contains(ControleImport.Probleme.AUCUNE_BARRE));
        ControleImport.GroupeSaisi sansType = groupe("4", "3", "N12", "4");
        sansType.type = TypeElementScan.INCONNU;
        assertTrue(ControleImport.problemes(sansType, TYPES).contains(ControleImport.Probleme.TYPE_MANQUANT));
    }

    @Test
    public void controleImport_avertitAuDelaDeDouzeMetres() {
        assertEquals(1, ControleImport.avertissements(groupe("1", "14", "N12", "2")).size());
        assertEquals(0, ControleImport.avertissements(groupe("1", "5,65", "N12", "2")).size());
    }

    @Test
    public void export_formatLuParLecranDeCalcul() {
        ControleImport.GroupeSaisi g = groupe("2", "5,65", "N14", "3", "N10", "3", "N14", "1");
        String json = EntreesCalcul.versJson(Arrays.asList(g));
        assertEquals("[{\"type\":\"poteau\",\"nombrePoteaux\":\"2\",\"longueurPoteaux\":\"5.65\","
                + "\"barres\":[{\"type\":\"N14\",\"quantite\":\"4\"},{\"type\":\"N10\",\"quantite\":\"3\"}]}]", json);
        assertEquals("poteau", EntreesCalcul.typePrincipal(Arrays.asList(g)));
        ControleImport.GroupeSaisi h = groupe("1", "3", "N12", "4");
        h.type = TypeElementScan.POUTRE;
        assertEquals("multi", EntreesCalcul.typePrincipal(Arrays.asList(g, h)));
    }

    @Test
    public void selectionPages() {
        assertEquals(Arrays.asList(4, 5, 6), SelectionPages.analyser("4-6", 27));
        assertEquals(Arrays.asList(1, 3, 5, 6, 7), SelectionPages.analyser("1, 3 ,5-7", 27));
        assertEquals(Arrays.asList(2), SelectionPages.analyser("2", 27));
        assertNull(SelectionPages.analyser("0-3", 27));
        assertNull(SelectionPages.analyser("5-3", 27));
        assertNull(SelectionPages.analyser("20-30", 27));
        assertNull(SelectionPages.analyser("abc", 27));
        assertNull(SelectionPages.analyser("", 27));
        assertNull(SelectionPages.analyser("1-70", 133));
    }
}