package com.example.gkm_bar_fer;

/**
 * Un mouvement du stock de déchets : ajout ou retrait, avec sa date.
 * Les mouvements d'une même opération (ex : une coupe = 1 retrait + 1 ajout du reste)
 * partagent le même identifiant de groupe pour être affichés dans un seul bloc.
 */
public class MouvementStock {
    public static final String AJOUT = "AJOUT";
    public static final String RETRAIT = "RETRAIT";

    public final long date;
    public final String action;
    public final String typeFer;
    public final double longueur;
    public final int quantite;
    public final String motif;
    public final String groupe;

    public MouvementStock(long date, String action, String typeFer,
                          double longueur, int quantite, String motif, String groupe) {
        this.date = date;
        this.action = action;
        this.typeFer = typeFer;
        this.longueur = longueur;
        this.quantite = quantite;
        this.motif = motif == null ? "" : motif;
        this.groupe = groupe == null ? "" : groupe;
    }
}