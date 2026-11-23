package com.example.gkm_bar_fer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Résumé des déchets (chutes) pour le rapport PDF : regroupe les chutes par longueur
 * et fabrique les lignes « 8 chutes de 7,5 m », « 2 chutes de 5 m »...
 *
 * Classe 100 % Java (aucune dépendance Android) : elle est testée par des tests unitaires.
 */
public final class DechetsResume {

    /** Nombre maximum de lignes affichées dans le bloc du rapport (le reste est résumé). */
    public static final int MAX_LIGNES = 4;

    private DechetsResume() {
    }

    /** Table vide : longueur en centimètres -> nombre de chutes, de la plus longue à la plus courte. */
    public static TreeMap<Long, Integer> nouveau() {
        return new TreeMap<>(Collections.reverseOrder());
    }

    /** Ajoute une chute (longueur en mètres). Une chute inférieure à 1 cm est ignorée. */
    public static void ajouter(Map<Long, Integer> groupes, double longueurMetres) {
        long cm = Math.round(longueurMetres * 100.0);
        if (cm <= 0) {
            return;
        }
        Integer n = groupes.get(cm);
        groupes.put(cm, n == null ? 1 : n + 1);
    }

    /**
     * Lignes à afficher : une par longueur (« 8 chutes de 7,5 m »). Au-delà de MAX_LIGNES
     * longueurs différentes, les dernières sont résumées (« + 3 autres longueurs »).
     */
    public static List<String> lignes(Map<Long, Integer> groupes) {
        List<String> lignes = new ArrayList<>();
        if (groupes.isEmpty()) {
            lignes.add("Aucun déchet");
            return lignes;
        }
        int total = groupes.size();
        int i = 0;
        for (Map.Entry<Long, Integer> e : groupes.entrySet()) {
            if (total > MAX_LIGNES && i == MAX_LIGNES - 1) {
                lignes.add("+ " + (total - i) + " autres longueurs");
                break;
            }
            int nombre = e.getValue();
            lignes.add(nombre + (nombre > 1 ? " chutes" : " chute")
                    + " de " + formater(e.getKey() / 100.0) + " m");
            i++;
        }
        return lignes;
    }

    /** 7.5 -> « 7,5 » ; 5.0 -> « 5 » ; 0.35 -> « 0,35 ». */
    static String formater(double metres) {
        String s = String.format(Locale.US, "%.2f", metres).replace('.', ',');
        s = s.replaceAll("0+$", "");
        if (s.endsWith(",")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
