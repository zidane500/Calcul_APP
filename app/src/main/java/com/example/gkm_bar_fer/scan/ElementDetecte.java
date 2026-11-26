package com.example.gkm_bar_fer.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un élément reconnu sur un plan (poteau P1, semelle S1, poutre n°18...) avec ses armatures
 * et le résultat des contrôles automatiques.
 */
public final class ElementDetecte {

    /** Fiabilité de la lecture : HAUTE seulement quand les totaux du plan ont pu être recoupés. */
    public enum Confiance { HAUTE, MOYENNE, BASSE }

    public final String repere;
    public final TypeElementScan type;
    /** Nombre d'éléments identiques, ou null s'il n'est pas sur le plan. */
    public final Integer nombre;
    public final String dimensions;
    public final List<BarreDetectee> barres;
    public final List<Alerte> alertes;
    public final Confiance confiance;
    public final int page;
    public final String source;

    public ElementDetecte(String repere, TypeElementScan type, Integer nombre, String dimensions,
                          List<BarreDetectee> barres, List<Alerte> alertes, Confiance confiance,
                          int page, String source) {
        this.repere = repere;
        this.type = type;
        this.nombre = nombre;
        this.dimensions = dimensions == null ? "" : dimensions;
        this.barres = Collections.unmodifiableList(new ArrayList<>(barres));
        this.alertes = Collections.unmodifiableList(new ArrayList<>(alertes));
        this.confiance = confiance;
        this.page = page;
        this.source = source == null ? "" : source;
    }

    public boolean aUneErreur() {
        for (Alerte a : alertes) {
            if (a.niveau == Alerte.Niveau.ERREUR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Découpe l'élément en groupes de calcul : un groupe par longueur de barre (et par sens) car
     * l'écran de calcul n'accepte qu'une longueur par groupe.
     */
    public List<GroupeDetecte> versGroupes() {
        Map<String, List<BarreDetectee>> parCle = new LinkedHashMap<>();
        for (BarreDetectee b : barres) {
            String cle = (b.longueurM == null ? "?" : String.format(Locale.US, "%.3f", b.longueurM))
                    + "|" + b.ensemble + "|" + b.sens;
            List<BarreDetectee> liste = parCle.get(cle);
            if (liste == null) {
                liste = new ArrayList<>();
                parCle.put(cle, liste);
            }
            liste.add(b);
        }

        List<GroupeDetecte> groupes = new ArrayList<>();
        for (List<BarreDetectee> liste : parCle.values()) {
            BarreDetectee premiere = liste.get(0);
            String libelle = repere;
            if (premiere.ensemble > 0) {
                libelle += " – armatures " + premiere.ensemble;
            }
            if (premiere.sens > 0) {
                libelle += " – sens " + premiere.sens;
            }
            // Même diamètre, même longueur : on additionne.
            Map<Integer, Integer> parDiametre = new LinkedHashMap<>();
            for (BarreDetectee b : liste) {
                Integer deja = parDiametre.get(b.diametre);
                parDiametre.put(b.diametre, (deja == null ? 0 : deja) + b.quantite);
            }
            List<BarreDetectee> fusionnees = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : parDiametre.entrySet()) {
                fusionnees.add(new BarreDetectee(e.getValue(), e.getKey(), premiere.longueurM,
                        premiere.role, premiere.ensemble, premiere.sens, ""));
            }
            groupes.add(new GroupeDetecte(libelle, type, nombre, premiere.longueurM, fusionnees, this));
        }
        return groupes;
    }
}