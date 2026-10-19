package com.example.gkm_bar_fer;

/**
 * Un mouvement du stock de déchets : ajout ou retrait, avec sa date.
 * Les mouvements d'une même opération (ex : une coupe = 1 retrait + 1 ajout du reste)
 * partagent le même identifiant de groupe pour être affichés dans un seul bloc.
 * Pour un mouvement issu d'un calcul fait dans l'espace d'un client, "client" contient le nom
 * du client (vide sinon : calcul libre ou mouvement manuel).
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
    public final String client;

    public MouvementStock(long date, String action, String typeFer,
                          double longueur, int quantite, String motif, String groupe) {
        this(date, action, typeFer, longueur, quantite, motif, groupe, "");
    }

    public MouvementStock(long date, String action, String typeFer,
                          double longueur, int quantite, String motif, String groupe,
                          String client) {
        this.date = date;
        this.action = action;
        this.typeFer = typeFer;
        this.longueur = longueur;
        this.quantite = quantite;
        this.motif = motif == null ? "" : motif;
        this.groupe = groupe == null ? "" : groupe;
        this.client = client == null ? "" : client.trim();
    }
}