package com.example.gkm_bar_fer.scan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dernier contrôle avant d'envoyer les groupes vers l'écran de calcul. Il porte sur ce que
 * l'utilisateur a validé à l'écran (il a pu corriger la lecture) : rien n'est importé si une
 * valeur est vide, nulle ou hors des types de barres de l'application.
 */
public final class ControleImport {

    private ControleImport() {
    }

    public enum Probleme {
        TYPE_MANQUANT,
        NOMBRE_INVALIDE,
        LONGUEUR_INVALIDE,
        AUCUNE_BARRE,
        BARRE_INCONNUE,
        QUANTITE_INVALIDE,
        /** Avertissement seulement : longueur supérieure à une barre de 12 m. */
        LONGUEUR_SUPERIEURE_BARRE
    }

    public static final class LigneBarre {
        public String designation;
        public String quantite;

        public LigneBarre(String designation, String quantite) {
            this.designation = designation;
            this.quantite = quantite;
        }
    }

    /** Un groupe tel qu'il est affiché et corrigé à l'écran de vérification. */
    public static final class GroupeSaisi {
        public TypeElementScan type = TypeElementScan.INCONNU;
        public String libelle = "";
        public String nombre = "";
        public String longueur = "";
        public final List<LigneBarre> barres = new ArrayList<>();
    }

    /** Problèmes bloquants (la liste vide signifie « importable »). */
    public static List<Probleme> problemes(GroupeSaisi g, Set<String> designationsAutorisees) {
        List<Probleme> res = new ArrayList<>();
        if (g.type == null || g.type == TypeElementScan.INCONNU) {
            res.add(Probleme.TYPE_MANQUANT);
        }
        Integer n = entier(g.nombre);
        if (n == null || n <= 0 || n > 100000) {
            res.add(Probleme.NOMBRE_INVALIDE);
        }
        Double l = decimal(g.longueur);
        if (l == null || l <= 0 || l > 100) {
            res.add(Probleme.LONGUEUR_INVALIDE);
        }
        if (g.barres.isEmpty()) {
            res.add(Probleme.AUCUNE_BARRE);
        }
        Set<Probleme> deja = new HashSet<>();
        for (LigneBarre b : g.barres) {
            if (b.designation == null || !designationsAutorisees.contains(b.designation)) {
                if (deja.add(Probleme.BARRE_INCONNUE)) {
                    res.add(Probleme.BARRE_INCONNUE);
                }
            }
            Integer q = entier(b.quantite);
            if (q == null || q <= 0 || q > 1000) {
                if (deja.add(Probleme.QUANTITE_INVALIDE)) {
                    res.add(Probleme.QUANTITE_INVALIDE);
                }
            }
        }
        return res;
    }

    /** Points à regarder sans bloquer. */
    public static List<Probleme> avertissements(GroupeSaisi g) {
        List<Probleme> res = new ArrayList<>();
        Double l = decimal(g.longueur);
        if (l != null && l > 12.0) {
            res.add(Probleme.LONGUEUR_SUPERIEURE_BARRE);
        }
        return res;
    }

    public static Integer entier(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double decimal(String s) {
        if (s == null) {
            return null;
        }
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}