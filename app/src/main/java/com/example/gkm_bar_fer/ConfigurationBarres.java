package com.example.gkm_bar_fer;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration centralisée des barres de fer, réutilisée par tous les écrans
 * (calcul, stock...) pour éviter de dupliquer ces valeurs.
 */
public final class ConfigurationBarres {

    public static final double LONGUEUR_BARRE_STANDARD = 12.0;

    private ConfigurationBarres() {
    }

    /** Liste des types de barres disponibles : N6 à N30 (pas de 2), puis N32, N35, N40. */
    public static List<String> listerTypes() {
        List<String> types = new ArrayList<>();
        for (int i = 6; i <= 30; i += 2) {
            types.add("N" + i);
        }
        types.add("N32");
        types.add("N35");
        types.add("N40");
        return types;
    }
}