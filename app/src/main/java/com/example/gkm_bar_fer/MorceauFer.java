package com.example.gkm_bar_fer;

/**
 * Représente une quantité de morceaux de fer d'une longueur donnée, pour un type de barre.
 * Une "Bar" entière (12 m) et une "Coupe" (longueur choisie) sont toutes deux représentées
 * de la même façon ici : seule l'interface de saisie diffère.
 */
public class MorceauFer {    public double longueur;
    public int quantite;

    // Vrai si ce morceau vient du stock d'un client (et non du stock global).
    // Utilisé seulement pendant un calcul qui combine les deux stocks.
    public boolean duClient = false;

    public MorceauFer(double longueur, int quantite) {
        this.longueur = longueur;
        this.quantite = quantite;
    }
}