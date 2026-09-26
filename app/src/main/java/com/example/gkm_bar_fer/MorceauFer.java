package com.example.gkm_bar_fer;

/**
 * Représente une quantité de morceaux de fer d'une longueur donnée, pour un type de barre.
 * Une "Bar" entière (12 m) et une "Coupe" (longueur choisie) sont toutes deux représentées
 * de la même façon ici : seule l'interface de saisie diffère.
 */
public class MorceauFer {
    public double longueur;
    public int quantite;

    public MorceauFer(double longueur, int quantite) {
        this.longueur = longueur;
        this.quantite = quantite;
    }
}