package com.example.gkm_bar_fer.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Un groupe prêt à être relu puis importé : un nombre d'éléments, une longueur, des barres. */
public final class GroupeDetecte {

    public final String libelle;
    public final TypeElementScan type;
    public final Integer nombre;
    public final Double longueurM;
    public final List<BarreDetectee> barres;
    public final ElementDetecte origine;

    public GroupeDetecte(String libelle, TypeElementScan type, Integer nombre, Double longueurM,
                         List<BarreDetectee> barres, ElementDetecte origine) {
        this.libelle = libelle;
        this.type = type;
        this.nombre = nombre;
        this.longueurM = longueurM;
        this.barres = Collections.unmodifiableList(new ArrayList<>(barres));
        this.origine = origine;
    }
}