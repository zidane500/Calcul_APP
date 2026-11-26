package com.example.gkm_bar_fer.scan;

/**
 * Une ligne d'armature lue sur le plan : « 4 barres de diamètre 12 ».
 * La longueur (en mètres) n'est renseignée que si le plan l'imprime (fiches de poutres).
 */
public final class BarreDetectee {

    public enum Role { LONGITUDINALE, CADRE }

    public final int quantite;
    public final int diametre;
    /** Longueur d'une barre en mètres, ou null si le plan ne la donne pas. */
    public final Double longueurM;
    public final Role role;
    /** Armature dite « 1 » ou « 2 » quand le plan donne deux sens (ex. « 11T8/7T8 »), 0 sinon. */
    public final int sens;
    /** Numéro du jeu d'armatures sur la ligne (1er, 2e « n/n ») ; 0 si un seul. */
    public final int ensemble;
    /** Texte tel que lu (pour le comparer au plan). */
    public final String source;

    public BarreDetectee(int quantite, int diametre, Double longueurM, Role role,
                         int ensemble, int sens, String source) {
        this.quantite = quantite;
        this.diametre = diametre;
        this.longueurM = longueurM;
        this.role = role;
        this.ensemble = ensemble;
        this.sens = sens;
        this.source = source == null ? "" : source;
    }

    /** Désignation utilisée par l'application : N12 pour un diamètre 12. */
    public String designation() {
        return "N" + diametre;
    }
}