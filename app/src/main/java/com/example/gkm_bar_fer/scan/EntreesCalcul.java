package com.example.gkm_bar_fer.scan;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Convertit les groupes validés en saisies pour l'écran de calcul. Le format est celui que cet
 * écran écrit déjà quand il enregistre un calcul (« type », « nombrePoteaux », « longueurPoteaux »,
 * « barres » avec « type » et « quantite »), donc il sait le relire sans changement.
 */
public final class EntreesCalcul {

    private EntreesCalcul() {
    }

    /** Code du type d'élément à passer à l'écran de calcul : « multi » si les types diffèrent. */
    public static String typePrincipal(List<ControleImport.GroupeSaisi> groupes) {
        Set<String> codes = new LinkedHashSet<>();
        for (ControleImport.GroupeSaisi g : groupes) {
            codes.add(g.type.getCode());
        }
        return codes.size() == 1 ? codes.iterator().next() : "multi";
    }

    /** Tableau JSON des groupes. Les groupes doivent avoir passé {@link ControleImport}. */
    public static String versJson(List<ControleImport.GroupeSaisi> groupes) {
        StringBuilder sb = new StringBuilder("[");
        boolean premier = true;
        for (ControleImport.GroupeSaisi g : groupes) {
            if (!premier) {
                sb.append(',');
            }
            premier = false;

            // Même désignation deux fois dans un groupe : on additionne.
            Map<String, Integer> barres = new LinkedHashMap<>();
            for (ControleImport.LigneBarre b : g.barres) {
                Integer deja = barres.get(b.designation);
                barres.put(b.designation, (deja == null ? 0 : deja) + ControleImport.entier(b.quantite));
            }

            sb.append("{\"type\":\"").append(g.type.getCode()).append("\",");
            sb.append("\"nombrePoteaux\":\"").append(ControleImport.entier(g.nombre)).append("\",");
            sb.append("\"longueurPoteaux\":\"").append(texteLongueur(ControleImport.decimal(g.longueur)))
                    .append("\",\"barres\":[");
            boolean pb = true;
            for (Map.Entry<String, Integer> e : barres.entrySet()) {
                if (!pb) {
                    sb.append(',');
                }
                pb = false;
                sb.append("{\"type\":\"").append(e.getKey()).append("\",\"quantite\":\"")
                        .append(e.getValue()).append("\"}");
            }
            sb.append("]}");
        }
        return sb.append(']').toString();
    }

    private static String texteLongueur(double v) {
        return BigDecimal.valueOf(v).setScale(3, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}