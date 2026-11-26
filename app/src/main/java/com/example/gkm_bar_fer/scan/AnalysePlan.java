package com.example.gkm_bar_fer.scan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit le texte reconnu (OCR) d'un plan de béton armé et en tire des éléments et leurs armatures.
 *
 * Classe 100 % Java (aucune dépendance Android) : elle se teste sur PC avec de vrais textes de plans.
 *
 * Principes :
 *  - on ne devine jamais une valeur absente du plan (nombre d'éléments, longueur) : on la laisse vide ;
 *  - tout ce qui peut être recoupé l'est (fiches de poutres : longueurs par diamètre et poids d'acier) ;
 *  - toute lecture douteuse porte une {@link Alerte} que l'écran de vérification affiche.
 */
public final class AnalysePlan {

    private AnalysePlan() {
    }

    /** Éléments trouvés sur une ou plusieurs pages, et indicateurs de lecture. */
    public static final class ResultatAnalyse {
        public final List<ElementDetecte> elements;
        /** Nombre de lignes d'armatures reconnues (sert à choisir le bon sens de lecture d'une page). */
        public final int score;

        public ResultatAnalyse(List<ElementDetecte> elements, int score) {
            this.elements = elements;
            this.score = score;
        }
    }

    // ------------------------------------------------------------------
    // Motifs
    // ------------------------------------------------------------------

    /** Un chiffre, éventuellement mal lu par l'OCR : O pour 0, I/l/L/i/| pour 1. */
    private static final String CH = "[0-9OoIiLl|]";

    private static final Set<Integer> DIAMETRES = new HashSet<>(Arrays.asList(
            5, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 25, 26, 28, 30, 32, 35, 40));

    /** « 4T12 », « 3HA10 », « #7T12 », « 4Ø12 » (le « t » parasite de « 3HAt10 » est toléré). */
    private static final Pattern BARRE = Pattern.compile(
            "(?<![A-Za-z0-9.,])(#\\s*)?(\\d{1,2})\\s*(?:HA|H\\.A|T|Ø)\\s*[tT.\\-']?\\s*(" + CH + "{1,2})(?![0-9])");

    /** Ligne d'une fiche Arche : « 3HA14 413 » (quantité, diamètre, longueur en cm). */
    private static final Pattern RANGEE_ARCHE = Pattern.compile(
            "(?<![A-Za-z0-9.,])(\\d{1,2})\\s*(?:HA|T)\\s*[tT.\\-']?\\s*(" + CH + "{1,2})\\s+(\\d{2,4})(?![0-9.,/])");

    /** Cadres d'une fiche Arche : « 15Dx8 82 » (quantité, diamètre, longueur en cm). */
    private static final Pattern RANGEE_ARCHE_CADRE = Pattern.compile(
            "(?<![A-Za-z0-9.,])(\\d{1,3})\\s*D\\s*[xX]\\s*(" + CH + "{1,2})\\s+(\\d{2,4})(?![0-9.,/])");

    /** Totaux de fin de fiche : « HA10 33,3/20.6 » ou « Dx8 65.2/25,8 » (mètres / kg). */
    private static final Pattern TOTAL_ARCHE = Pattern.compile(
            "(?<![A-Za-z0-9])(?:(HA)\\s*(" + CH + "{1,2})|(D)\\s*[xX]\\s*(" + CH + "{1,2}))\\s+"
                    + "(\\d{1,4}(?:[.,]\\d)?)\\s*/\\s*(\\d{1,4}(?:[.,]\\d)?)");

    private static final Pattern ACIER_KG = Pattern.compile(
            "Acier\\s*=\\s*(\\d{1,5}(?:[.,]\\d)?)\\s*kg", Pattern.CASE_INSENSITIVE);

    private static final Pattern TITRE_POUTRE = Pattern.compile(
            "Poutre\\s*n\\s*[°oO0]?\\s*(" + CH + "{1,3})");

    /** « A1 A A2 » : poutres A1 à A2. */
    private static final Pattern PLAGE_REPERES = Pattern.compile(
            "(?<![A-Za-z0-9])([A-Z])\\s?(" + CH + "{1,2})\\s+(?:A|À)\\s+([A-Z])\\s?(" + CH + "{1,2})(?![A-Za-z0-9])");

    /** Repère d'élément : P1, PP1, S1, SJ1, L2, CH1, A3, R1. */
    private static final Pattern REPERE = Pattern.compile(
            "(?<![A-Za-z0-9])(PP|SJ|CH|P|S|L|A|R)\\s?(\\d{1,2})(?![A-Za-z0-9])");

    private static final Pattern DIMENSIONS = Pattern.compile(
            "\\b\\d{2,3}\\s*[xX]\\s*\\d{2,3}(?:\\s*[xX]\\s*(?:\\d{1,3}|H))?\\b|Ø\\s*\\d{2,3}");

    private static final Pattern AU_METRE_OU_ESPACEMENT = Pattern.compile(
            "^\\s*(?:/\\s*m(?:l)?\\b|pm\\b|ml\\b|e\\s*=|e\\s*\\d)", Pattern.CASE_INSENSITIVE);

    private static final Pattern MOT_CADRE = Pattern.compile(
            "cad(?:re)?s?\\b|cad\\.|\\u00e9trier|etrier|cerce", Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------------
    // Point d'entrée
    // ------------------------------------------------------------------

    /**
     * Analyse les lignes d'une page.
     *
     * @param lignesBrutes lignes telles que renvoyées par l'OCR
     * @param page         numéro de la page (affiché dans les alertes)
     */
    public static ResultatAnalyse analyser(List<String> lignesBrutes, int page) {
        List<String> lignes = new ArrayList<>();
        for (String l : lignesBrutes) {
            String n = normaliser(l);
            if (!n.isEmpty()) {
                lignes.add(n);
            }
        }

        ElementDetecte fiche = lireFicheArche(lignes, page);
        List<ElementDetecte> elements = new ArrayList<>();
        int score;
        if (fiche != null) {
            elements.add(fiche);
            score = 3 * fiche.barres.size() + 2;
        } else {
            elements.addAll(lireTableauxEtAnnotations(lignes, page));
            score = 0;
            for (ElementDetecte e : elements) {
                score += e.barres.size();
            }
        }
        return new ResultatAnalyse(elements, score);
    }

    /**
     * Note la qualité d'une lecture : sert à choisir, parmi plusieurs sens de rotation de la page,
     * celui où l'OCR a le mieux lu les armatures.
     */
    public static int scorer(List<String> lignesBrutes) {
        int score = 0;
        for (String brute : lignesBrutes) {
            String l = normaliser(brute);
            score += trouverBarres(l).size();
            Matcher m = RANGEE_ARCHE.matcher(l);
            while (m.find()) {
                score += 2;
            }
            m = RANGEE_ARCHE_CADRE.matcher(l);
            while (m.find()) {
                score += 2;
            }
            if (ACIER_KG.matcher(l).find()) {
                score += 3;
            }
            if (TOTAL_ARCHE.matcher(l).find()) {
                score += 2;
            }
        }
        return score;
    }

    // ------------------------------------------------------------------
    // Normalisation
    // ------------------------------------------------------------------

    static String normaliser(String s) {
        if (s == null) {
            return "";
        }
        String t = s
                .replace('\u00D7', 'x').replace('\u2715', 'x').replace('\u0425', 'X')
                .replace('\u03A6', '\u00D8').replace('\u0424', '\u00D8').replace('\u00F8', '\u00D8')
                .replace('\u2205', '\u00D8').replace('\u2300', '\u00D8').replace('\u03D5', '\u00D8')
                .replace('\u03C6', '\u00D8')
                .replace('\u00A0', ' ').replace('\u2019', '\'').replace('\u2018', '\'');
        return t.replaceAll("\\s+", " ").trim();
    }

    /** Valeur d'un nombre lu par l'OCR (O→0, I/l/L/i/|→1), ou -1 si illisible. */
    static int chiffres(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == 'O' || c == 'o') {
                sb.append('0');
            } else if (c == 'I' || c == 'i' || c == 'l' || c == 'L' || c == '|') {
                sb.append('1');
            } else if (Character.isDigit(c)) {
                sb.append(c);
            } else {
                return -1;
            }
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double decimal(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private static String formatM(double m) {
        return String.format(Locale.FRANCE, "%.1f", m);
    }

    // ------------------------------------------------------------------
    // Armatures sur une ligne
    // ------------------------------------------------------------------

    /** Une armature trouvée dans une ligne, avec sa position. */
    private static final class Trouvee {
        int quantite;
        int diametre;
        boolean grille;
        boolean auMetre;
        int debut;
        int fin;
    }

    private static List<Trouvee> trouverBarres(String ligne) {
        List<Trouvee> res = new ArrayList<>();
        Matcher m = BARRE.matcher(ligne);
        while (m.find()) {
            int quantite = Integer.parseInt(m.group(2));
            int diametre = chiffres(m.group(3));
            if (quantite < 1 || quantite > 60 || !DIAMETRES.contains(diametre)) {
                continue;
            }
            Trouvee t = new Trouvee();
            t.quantite = quantite;
            t.diametre = diametre;
            t.grille = m.group(1) != null;
            t.debut = m.start();
            t.fin = m.end();
            t.auMetre = AU_METRE_OU_ESPACEMENT.matcher(ligne.substring(m.end())).find();
            res.add(t);
        }
        return res;
    }

    // ------------------------------------------------------------------
    // Fiches de poutres « Arche » : lecture + contrôle par les totaux imprimés
    // ------------------------------------------------------------------

    private static ElementDetecte lireFicheArche(List<String> lignes, int page) {
        List<BarreDetectee> barres = new ArrayList<>();
        StringBuilder source = new StringBuilder();
        boolean motCle = false;

        for (String l : lignes) {
            if (l.contains("Arche") || l.contains("BAEL") || ACIER_KG.matcher(l).find()) {
                motCle = true;
            }
            Matcher m = RANGEE_ARCHE.matcher(l);
            while (m.find()) {
                int quantite = Integer.parseInt(m.group(1));
                int diametre = chiffres(m.group(2));
                int cm = Integer.parseInt(m.group(3));
                if (quantite < 1 || !DIAMETRES.contains(diametre) || cm < 20 || cm > 1500) {
                    continue;
                }
                barres.add(new BarreDetectee(quantite, diametre, cm / 100.0,
                        BarreDetectee.Role.LONGITUDINALE, 0, 0, m.group().trim()));
                source.append(m.group().trim()).append('\n');
            }
            m = RANGEE_ARCHE_CADRE.matcher(l);
            while (m.find()) {
                int quantite = Integer.parseInt(m.group(1));
                int diametre = chiffres(m.group(2));
                int cm = Integer.parseInt(m.group(3));
                if (quantite < 1 || !DIAMETRES.contains(diametre) || cm < 20 || cm > 1500) {
                    continue;
                }
                barres.add(new BarreDetectee(quantite, diametre, cm / 100.0,
                        BarreDetectee.Role.CADRE, 0, 0, m.group().trim()));
                source.append(m.group().trim()).append('\n');
            }
        }

        Map<String, double[]> totaux = lireTotaux(lignes);
        if (barres.isEmpty() || !(motCle || !totaux.isEmpty())) {
            return null;
        }

        List<Alerte> alertes = new ArrayList<>();

        // --- Repère et nombre de poutres identiques ---
        String repere = null;
        Integer nombre = null;
        for (String l : lignes) {
            Matcher t = TITRE_POUTRE.matcher(l);
            if (repere == null && t.find()) {
                int n = chiffres(t.group(1));
                if (n >= 0) {
                    repere = "Poutre n°" + n;
                }
            }
            Matcher p = PLAGE_REPERES.matcher(l);
            if (nombre == null && p.find() && p.group(1).equals(p.group(3))) {
                int a = chiffres(p.group(2));
                int b = chiffres(p.group(4));
                if (a >= 0 && b >= a && b - a < 50) {
                    nombre = b - a + 1;
                    String libelle = p.group(1) + a + " à " + p.group(3) + b;
                    repere = (repere == null ? "Poutre" : repere) + " (" + libelle + ")";
                    alertes.add(new Alerte(Alerte.Niveau.INFO,
                            "Repères lus : " + libelle + " → " + nombre + " poutre(s) identique(s)."));
                }
            }
        }
        if (repere == null) {
            repere = "Poutre (fiche page " + page + ")";
        }
        if (nombre == null) {
            nombre = 1;
            alertes.add(new Alerte(Alerte.Niveau.ATTENTION,
                    "Nombre de poutres identiques non lu : 1 par défaut. Vérifier le cartouche de la fiche."));
        }

        // --- Contrôles par les totaux imprimés ---
        Map<String, Double> lu = new LinkedHashMap<>();
        for (BarreDetectee b : barres) {
            String cle = cleTotal(b.role, b.diametre);
            Double deja = lu.get(cle);
            lu.put(cle, (deja == null ? 0.0 : deja) + b.quantite * b.longueurM);
        }

        int controles = 0;
        boolean echec = false;
        Set<String> cles = new java.util.TreeSet<>(lu.keySet());
        cles.addAll(totaux.keySet());
        for (String cle : cles) {
            double[] total = totaux.get(cle);
            if (total == null) {
                continue;
            }
            double metresLus = lu.containsKey(cle) ? lu.get(cle) : 0.0;
            double tolerance = Math.max(0.15, 0.015 * total[0]);
            double ecart = metresLus - total[0];
            controles++;
            if (Math.abs(ecart) <= tolerance) {
                continue;
            }
            echec = true;
            String nom = cle.startsWith("D") ? "cadres Ø" + cle.substring(1) : "Ø" + cle.substring(2);
            if (ecart < 0) {
                alertes.add(new Alerte(Alerte.Niveau.ERREUR, "Contrôle " + nom + " : "
                        + formatM(metresLus) + " m lus, " + formatM(total[0]) + " m sur le plan — il manque ≈ "
                        + formatM(-ecart) + " m (ligne(s) non lue(s)). À compléter avant d'importer."));
            } else {
                alertes.add(new Alerte(Alerte.Niveau.ERREUR, "Contrôle " + nom + " : "
                        + formatM(metresLus) + " m lus, seulement " + formatM(total[0])
                        + " m sur le plan — une valeur a été mal lue."));
            }
        }

        Double acier = lireAcierKg(lignes);
        if (acier != null) {
            double kg = 0;
            for (BarreDetectee b : barres) {
                kg += b.quantite * b.longueurM * PoidsAcier.kgParMetre(b.diametre);
            }
            double tolerance = Math.max(0.3, 0.02 * acier);
            controles++;
            if (Math.abs(kg - acier) > tolerance) {
                echec = true;
                alertes.add(new Alerte(Alerte.Niveau.ERREUR, "Contrôle du poids : " + formatM(kg)
                        + " kg d'après les lignes lues, " + formatM(acier) + " kg imprimés sur le plan."));
            }
        }

        ElementDetecte.Confiance confiance;
        if (echec) {
            confiance = ElementDetecte.Confiance.BASSE;
        } else if (controles == 0) {
            confiance = ElementDetecte.Confiance.MOYENNE;
            alertes.add(new Alerte(Alerte.Niveau.ATTENTION,
                    "Totaux du plan non lisibles : aucun contrôle automatique possible. "
                            + "Comparer chaque ligne avec la fiche."));
        } else {
            confiance = ElementDetecte.Confiance.HAUTE;
            alertes.add(new Alerte(Alerte.Niveau.INFO, "Contrôle réussi : " + controles
                    + " vérification(s) (longueurs par diamètre et poids d'acier) conformes au plan."));
        }

        return new ElementDetecte(repere, TypeElementScan.POUTRE, nombre, "", barres, alertes,
                confiance, page, source.toString().trim());
    }

    private static String cleTotal(BarreDetectee.Role role, int diametre) {
        return (role == BarreDetectee.Role.CADRE ? "D" : "HA") + diametre;
    }

    /** Totaux « HA10 33,3/20.6 » : clé → {mètres, kg}. */
    private static Map<String, double[]> lireTotaux(List<String> lignes) {
        Map<String, double[]> res = new LinkedHashMap<>();
        for (String l : lignes) {
            Matcher m = TOTAL_ARCHE.matcher(l);
            while (m.find()) {
                boolean cadre = m.group(3) != null;
                int d = chiffres(cadre ? m.group(4) : m.group(2));
                if (!DIAMETRES.contains(d)) {
                    continue;
                }
                res.put((cadre ? "D" : "HA") + d,
                        new double[]{decimal(m.group(5)), decimal(m.group(6))});
            }
        }
        return res;
    }

    private static Double lireAcierKg(List<String> lignes) {
        for (String l : lignes) {
            Matcher m = ACIER_KG.matcher(l);
            if (m.find()) {
                return decimal(m.group(1));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Tableaux de ferraillage et annotations : « P1 (22x22) 4T12 cad Ø6/15 »
    // ------------------------------------------------------------------

    private static final class Courant {
        String repere;
        TypeElementScan type;
        final List<String> dimensions = new ArrayList<>();
        final List<BarreDetectee> barres = new ArrayList<>();
        final List<Alerte> alertes = new ArrayList<>();
        final StringBuilder source = new StringBuilder();
        boolean surAutreLigne;
        boolean cadresVus;
        boolean grille;
        boolean deuxSens;
        boolean auMetre;
    }

    private static List<ElementDetecte> lireTableauxEtAnnotations(List<String> lignes, int page) {
        List<ElementDetecte> res = new ArrayList<>();
        Courant courant = null;
        int distance = 0;

        for (String l : lignes) {
            List<int[]> reperes = new ArrayList<>();
            Matcher r = REPERE.matcher(l);
            while (r.find()) {
                reperes.add(new int[]{r.start(), r.end()});
            }

            if (!reperes.isEmpty()) {
                for (int k = 0; k < reperes.size(); k++) {
                    cloturer(courant, page, res);
                    int debut = reperes.get(k)[0];
                    int fin = k + 1 < reperes.size() ? reperes.get(k + 1)[0] : l.length();
                    String segment = l.substring(debut, fin);
                    Matcher rm = REPERE.matcher(segment);
                    rm.find();
                    courant = new Courant();
                    courant.repere = rm.group(1) + rm.group(2);
                    courant.type = typeDepuisRepere(rm.group(1));
                    courant.source.append(segment.trim()).append('\n');
                    ajouterDansCourant(courant, segment.substring(rm.end()), false);
                    distance = 0;
                }
            } else if (courant != null) {
                boolean avait = !courant.barres.isEmpty();
                int avant = courant.barres.size();
                ajouterDansCourant(courant, l, true);
                if (courant.barres.size() > avant || MOT_CADRE.matcher(l).find()) {
                    courant.source.append(l).append('\n');
                    distance = Math.max(0, distance - 1);
                } else {
                    distance++;
                }
                if (distance > 3) {
                    cloturer(courant, page, res);
                    courant = null;
                    distance = 0;
                }
            }
        }
        cloturer(courant, page, res);
        return res;
    }

    private static void ajouterDansCourant(Courant c, String texte, boolean autreLigne) {
        Matcher d = DIMENSIONS.matcher(texte);
        while (d.find()) {
            String dim = d.group().replace(" ", "");
            if (!c.dimensions.contains(dim)) {
                c.dimensions.add(dim);
            }
        }
        if (MOT_CADRE.matcher(texte).find()) {
            c.cadresVus = true;
        }

        List<Trouvee> trouvees = trouverBarres(texte);
        // « 11T8/7T8 » : même diamètre de part et d'autre du « / » = deux sens.
        int ensembles = 0;
        List<int[]> sensParTrouvee = new ArrayList<>();
        for (int i = 0; i < trouvees.size(); i++) {
            Trouvee t = trouvees.get(i);
            boolean suivantMemeDiametre = i + 1 < trouvees.size()
                    && trouvees.get(i + 1).diametre == t.diametre
                    && texte.substring(t.fin, trouvees.get(i + 1).debut).trim().equals("/");
            if (suivantMemeDiametre) {
                ensembles++;
                sensParTrouvee.add(new int[]{ensembles, 1});
                sensParTrouvee.add(new int[]{ensembles, 2});
                i++;
                c.deuxSens = true;
            } else {
                sensParTrouvee.add(new int[]{0, 0});
            }
        }
        // Si un seul jeu « n/n » sur la ligne, pas besoin de numéroter l'ensemble.
        if (ensembles == 1) {
            for (int[] s : sensParTrouvee) {
                if (s[1] > 0) {
                    s[0] = 0;
                }
            }
        }

        for (int i = 0; i < trouvees.size(); i++) {
            Trouvee t = trouvees.get(i);
            if (t.auMetre) {
                c.auMetre = true;
                continue;
            }
            int[] s = sensParTrouvee.get(i);
            int quantite = t.quantite;
            if (t.grille) {
                c.grille = true;
                quantite = t.quantite * 2;
            }
            c.barres.add(new BarreDetectee(quantite, t.diametre, null,
                    BarreDetectee.Role.LONGITUDINALE, s[0], s[1],
                    texte.substring(t.debut, t.fin).trim()));
            if (autreLigne) {
                c.surAutreLigne = true;
            }
        }
    }

    private static void cloturer(Courant c, int page, List<ElementDetecte> res) {
        if (c == null || c.barres.isEmpty()) {
            return;
        }
        List<Alerte> alertes = new ArrayList<>(c.alertes);
        if (c.type != TypeElementScan.INCONNU) {
            alertes.add(new Alerte(Alerte.Niveau.INFO,
                    "Type déduit du repère « " + c.repere + " » : à confirmer."));
        }
        alertes.add(new Alerte(Alerte.Niveau.ATTENTION,
                "Le plan ne donne ni le nombre d'éléments ni la longueur des barres : à saisir."));
        if (c.surAutreLigne) {
            alertes.add(new Alerte(Alerte.Niveau.ATTENTION,
                    "Armatures associées au repère par ordre de lecture : vérifier sur le plan."));
        }
        if (c.grille) {
            alertes.add(new Alerte(Alerte.Niveau.ATTENTION,
                    "Grille « # » : le nombre indiqué est valable dans chaque sens, la quantité "
                            + "reprise ici est doublée."));
        }
        if (c.deuxSens) {
            alertes.add(new Alerte(Alerte.Niveau.ATTENTION,
                    "Deux sens « n/n » : un groupe par sens, la longueur peut différer."));
        }
        if (c.auMetre) {
            alertes.add(new Alerte(Alerte.Niveau.INFO,
                    "Armatures « au mètre » ou espacées (treillis, nappes) ignorées : à calculer à part."));
        }
        if (c.cadresVus) {
            alertes.add(new Alerte(Alerte.Niveau.INFO,
                    "Cadres / cerces à espacement : non importés (quantité selon la longueur)."));
        }
        ElementDetecte.Confiance confiance = c.surAutreLigne
                ? ElementDetecte.Confiance.BASSE : ElementDetecte.Confiance.MOYENNE;
        res.add(new ElementDetecte(c.repere, c.type, null, joindre(c.dimensions), c.barres, alertes,
                confiance, page, c.source.toString().trim()));
    }

    private static String joindre(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) {
                sb.append(" ; ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static TypeElementScan typeDepuisRepere(String prefixe) {
        switch (prefixe) {
            case "P":
                return TypeElementScan.POTEAU;
            case "PP":
                return TypeElementScan.PRE_POTEAU;
            case "S":
            case "SJ":
                return TypeElementScan.SEMELLE;
            case "CH":
                return TypeElementScan.CHAINAGE;
            case "L":
            case "A":
            case "R":
                return TypeElementScan.POUTRE;
            default:
                return TypeElementScan.INCONNU;
        }
    }

    // ------------------------------------------------------------------
    // Fusion de plusieurs pages
    // ------------------------------------------------------------------

    /**
     * Regroupe les éléments de plusieurs pages : le même repère avec les mêmes armatures n'est gardé
     * qu'une fois ; le même repère avec des armatures différentes reste en double avec une erreur.
     */
    public static List<ElementDetecte> fusionner(List<ElementDetecte> tous) {
        Map<String, List<ElementDetecte>> parRepere = new LinkedHashMap<>();
        for (ElementDetecte e : tous) {
            String cle = e.type.getCode() + "|" + e.repere;
            List<ElementDetecte> liste = parRepere.get(cle);
            if (liste == null) {
                liste = new ArrayList<>();
                parRepere.put(cle, liste);
            }
            liste.add(e);
        }

        List<ElementDetecte> res = new ArrayList<>();
        for (List<ElementDetecte> liste : parRepere.values()) {
            if (liste.size() == 1) {
                res.add(liste.get(0));
                continue;
            }
            Map<String, List<ElementDetecte>> parSignature = new LinkedHashMap<>();
            for (ElementDetecte e : liste) {
                String sig = signature(e);
                List<ElementDetecte> l2 = parSignature.get(sig);
                if (l2 == null) {
                    l2 = new ArrayList<>();
                    parSignature.put(sig, l2);
                }
                l2.add(e);
            }
            boolean conflit = parSignature.size() > 1;
            for (List<ElementDetecte> identiques : parSignature.values()) {
                ElementDetecte premier = identiques.get(0);
                List<Alerte> alertes = new ArrayList<>(premier.alertes);
                if (identiques.size() > 1) {
                    alertes.add(new Alerte(Alerte.Niveau.INFO, "Repère lu " + identiques.size()
                            + " fois avec les mêmes armatures : gardé une seule fois."));
                }
                if (conflit) {
                    alertes.add(new Alerte(Alerte.Niveau.ERREUR, "« " + premier.repere
                            + " » apparaît avec des armatures différentes (pages différentes) : "
                            + "vérifier lequel est correct."));
                }
                res.add(new ElementDetecte(premier.repere, premier.type, premier.nombre,
                        premier.dimensions, premier.barres, alertes,
                        conflit ? ElementDetecte.Confiance.BASSE : premier.confiance,
                        premier.page, premier.source));
            }
        }
        return res;
    }

    private static String signature(ElementDetecte e) {
        List<String> parts = new ArrayList<>();
        for (BarreDetectee b : e.barres) {
            parts.add(b.quantite + "N" + b.diametre + "|" + b.longueurM + "|" + b.ensemble + "|" + b.sens);
        }
        java.util.Collections.sort(parts);
        return parts.toString();
    }
}