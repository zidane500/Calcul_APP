package com.example.gkm_bar_fer.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Lit une saisie de pages comme « 4-21 » ou « 1,3,5-7 ». */
public final class SelectionPages {

    /** Nombre maximal de pages analysées d'un coup (temps et mémoire du téléphone). */
    public static final int MAX_PAGES = 60;

    private SelectionPages() {
    }

    /** Pages demandées, triées et sans doublon ; null si la saisie est invalide ou trop longue. */
    public static List<Integer> analyser(String texte, int nbPages) {
        if (texte == null || texte.trim().isEmpty() || nbPages < 1) {
            return null;
        }
        TreeSet<Integer> pages = new TreeSet<>();
        for (String morceau : texte.trim().split("[,;\\s]+")) {
            if (morceau.isEmpty()) {
                continue;
            }
            try {
                int tiret = morceau.indexOf('-');
                int debut;
                int fin;
                if (tiret < 0) {
                    debut = Integer.parseInt(morceau);
                    fin = debut;
                } else {
                    debut = Integer.parseInt(morceau.substring(0, tiret));
                    fin = Integer.parseInt(morceau.substring(tiret + 1));
                }
                if (debut < 1 || fin < debut || fin > nbPages) {
                    return null;
                }
                for (int p = debut; p <= fin; p++) {
                    pages.add(p);
                    if (pages.size() > MAX_PAGES) {
                        return null;
                    }
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return pages.isEmpty() ? null : new ArrayList<>(pages);
    }
}