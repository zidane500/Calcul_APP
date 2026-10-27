package com.example.gkm_bar_fer;

import android.content.Context;

/**
 * Les types d'éléments de construction pour lesquels on calcule les barres de fer.
 *
 * Les libellés viennent des fichiers de ressources (res/values/types.xml et
 * res/values-ar/types.xml) : ils suivent donc la langue de l'application. Les anciens accesseurs
 * sans Context (getSingulier()...) restent disponibles et renvoient toujours le français.
 */
public enum TypeElement {

    POTEAU("poteau", "poteaux", false, "poteau",
            R.string.type_poteau_singulier, R.string.type_poteau_defini, R.string.type_poteau_pluriel_defini,
            R.string.type_poteau_infos, R.string.type_poteau_nombre, R.string.type_poteau_longueur,
            R.string.type_poteau_ajouter, R.string.type_poteau_supprimer, R.string.type_poteau_titre_calcul,
            R.plurals.nb_poteau, R.plurals.nbg_poteau),
    DALLE("dalle", "dalles", true, "dalle",
            R.string.type_dalle_singulier, R.string.type_dalle_defini, R.string.type_dalle_pluriel_defini,
            R.string.type_dalle_infos, R.string.type_dalle_nombre, R.string.type_dalle_longueur,
            R.string.type_dalle_ajouter, R.string.type_dalle_supprimer, R.string.type_dalle_titre_calcul,
            R.plurals.nb_dalle, R.plurals.nbg_dalle),
    SEMELLE("semelle", "semelles", true, "semelle",
            R.string.type_semelle_singulier, R.string.type_semelle_defini, R.string.type_semelle_pluriel_defini,
            R.string.type_semelle_infos, R.string.type_semelle_nombre, R.string.type_semelle_longueur,
            R.string.type_semelle_ajouter, R.string.type_semelle_supprimer, R.string.type_semelle_titre_calcul,
            R.plurals.nb_semelle, R.plurals.nbg_semelle),
    POUTRE("poutre", "poutres", true, "poutre",
            R.string.type_poutre_singulier, R.string.type_poutre_defini, R.string.type_poutre_pluriel_defini,
            R.string.type_poutre_infos, R.string.type_poutre_nombre, R.string.type_poutre_longueur,
            R.string.type_poutre_ajouter, R.string.type_poutre_supprimer, R.string.type_poutre_titre_calcul,
            R.plurals.nb_poutre, R.plurals.nbg_poutre),
    PRE_POTEAU("pré-poteau", "pré-poteaux", false, "pre_poteau",
            R.string.type_pre_poteau_singulier, R.string.type_pre_poteau_defini, R.string.type_pre_poteau_pluriel_defini,
            R.string.type_pre_poteau_infos, R.string.type_pre_poteau_nombre, R.string.type_pre_poteau_longueur,
            R.string.type_pre_poteau_ajouter, R.string.type_pre_poteau_supprimer, R.string.type_pre_poteau_titre_calcul,
            R.plurals.nb_pre_poteau, R.plurals.nbg_pre_poteau),

    CHAINAGE("chaînage", "chaînages", false, "chainage",
            R.string.type_chainage_singulier, R.string.type_chainage_defini, R.string.type_chainage_pluriel_defini,
            R.string.type_chainage_infos, R.string.type_chainage_nombre, R.string.type_chainage_longueur,
            R.string.type_chainage_ajouter, R.string.type_chainage_supprimer, R.string.type_chainage_titre_calcul,
            R.plurals.nb_chainage, R.plurals.nbg_chainage),
    ESCALIER("escalier", "escaliers", false, "escalier",
            R.string.type_escalier_singulier, R.string.type_escalier_defini, R.string.type_escalier_pluriel_defini,
            R.string.type_escalier_infos, R.string.type_escalier_nombre, R.string.type_escalier_longueur,
            R.string.type_escalier_ajouter, R.string.type_escalier_supprimer, R.string.type_escalier_titre_calcul,
            R.plurals.nb_escalier, R.plurals.nbg_escalier);


    private final String singulier;
    private final String pluriel;
    private final boolean feminin;
    private final String code;

    private final int resSingulier;
    private final int resDefini;
    private final int resPlurielDefini;
    private final int resInfos;
    private final int resNombre;
    private final int resLongueur;
    private final int resAjouter;
    private final int resSupprimer;
    private final int resTitreCalcul;
    private final int resNombreElements;   // plurals : "%d poteau(x)"
    private final int resNombreGenitif;    // idem, forme après une préposition (arabe : duel au génitif)

    TypeElement(String singulier, String pluriel, boolean feminin, String code,
                int resSingulier, int resDefini, int resPlurielDefini,
                int resInfos, int resNombre, int resLongueur,
                int resAjouter, int resSupprimer, int resTitreCalcul,
                int resNombreElements, int resNombreGenitif) {
        this.singulier = singulier;
        this.pluriel = pluriel;
        this.feminin = feminin;
        this.code = code;
        this.resSingulier = resSingulier;
        this.resDefini = resDefini;
        this.resPlurielDefini = resPlurielDefini;
        this.resInfos = resInfos;
        this.resNombre = resNombre;
        this.resLongueur = resLongueur;
        this.resAjouter = resAjouter;
        this.resSupprimer = resSupprimer;
        this.resTitreCalcul = resTitreCalcul;
        this.resNombreElements = resNombreElements;
        this.resNombreGenitif = resNombreGenitif;
    }

    /** Code stocké dans les Intent et dans les calculs enregistrés (sans accent ni tiret). */
    public String getCode() {
        return code;
    }

    /** Retrouve un type à partir de son code. Par défaut : POTEAU. */
    public static TypeElement depuisCode(String code) {
        if (code != null) {
            for (TypeElement type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
        }
        return POTEAU;
    }

    // ------------------------------------------------------------------
    // Libellés dans la langue du Context (français ou arabe)
    // ------------------------------------------------------------------

    /** ex : poteau / عمود */
    public String singulier(Context c) {
        return c.getString(resSingulier);
    }

    public String singulierMajuscule(Context c) {
        return majuscule(singulier(c));
    }

    /** ex : le poteau / العمود */
    public String defini(Context c) {
        return c.getString(resDefini);
    }

    /** ex : poteaux / الأعمدة */
    public String plurielDefini(Context c) {
        return c.getString(resPlurielDefini);
    }

    public String plurielDefiniMajuscule(Context c) {
        return majuscule(plurielDefini(c));
    }

    /** ex : Informations des Poteaux */
    public String infos(Context c) {
        return c.getString(resInfos);
    }

    /** ex : Nombre de poteaux */
    public String nombre(Context c) {
        return c.getString(resNombre);
    }

    /** ex : Longueur de poteau */
    public String longueur(Context c) {
        return c.getString(resLongueur);
    }

    /** ex : Ajouter un autre poteau */
    public String ajouter(Context c) {
        return c.getString(resAjouter);
    }

    /** ex : Supprimer ce poteau */
    public String supprimer(Context c) {
        return c.getString(resSupprimer);
    }

    /** ex : Calcul poteau - Hama */
    public String titreCalcul(Context c, String nomClient) {
        return c.getString(resTitreCalcul, nomClient);
    }

    /** ex : "5 poteaux" (en arabe : "5 أعمدة", "عمودان", "11 عمودا"...) */
    public String compte(Context c, int nombre) {
        return c.getResources().getQuantityString(resNombreElements, nombre, nombre);
    }

    /** Comme compte(), pour une phrase où le nom suit une préposition ("pour 2 poutres" / "لأجل رافدتين"). */
    public String compteGenitif(Context c, int nombre) {
        return c.getResources().getQuantityString(resNombreGenitif, nombre, nombre);
    }

    // ------------------------------------------------------------------
    // Anciens accesseurs : toujours en français
    // ------------------------------------------------------------------

    /** ex : poteau */
    public String getSingulier() {
        return singulier;
    }

    /** ex : poteaux */
    public String getPluriel() {
        return pluriel;
    }

    /** ex : Poteau */
    public String getSingulierMajuscule() {
        return majuscule(singulier);
    }

    /** ex : Poteaux */
    public String getPlurielMajuscule() {
        return majuscule(pluriel);
    }

    /** ex : "un autre" / "une autre" */
    public String getUnAutre() {
        return feminin ? "une autre" : "un autre";
    }

    /** ex : "ce" / "cette" */
    public String getCe() {
        return feminin ? "cette" : "ce";
    }

    /** ex : "le poteau", "la dalle", "l'escalier" */
    public String getLeSingulier() {
        if (this == ESCALIER) {
            return "l'" + singulier;
        }
        return (feminin ? "la " : "le ") + singulier;
    }

    private static String majuscule(String texte) {
        return texte.substring(0, 1).toUpperCase() + texte.substring(1);
    }
}
