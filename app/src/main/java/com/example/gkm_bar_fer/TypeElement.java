package com.example.gkm_bar_fer;

/**
 * Les types d'éléments de construction pour lesquels on calcule les barres de fer.
 * Sert à adapter les libellés de l'écran de calcul (poteau, dalle, semelle...).
 */
public enum TypeElement {

    POTEAU("poteau", "poteaux", false),
    DALLE("dalle", "dalles", true),
    SEMELLE("semelle", "semelles", true),
    ESCALIER("escalier", "escaliers", false);

    private final String singulier;
    private final String pluriel;
    private final boolean feminin;

    TypeElement(String singulier, String pluriel, boolean feminin) {
        this.singulier = singulier;
        this.pluriel = pluriel;
        this.feminin = feminin;
    }

    /** Code stocké dans les Intent et dans les calculs enregistrés. */
    public String getCode() {
        return singulier;
    }

    /** Retrouve un type à partir de son code. Par défaut : POTEAU. */
    public static TypeElement depuisCode(String code) {
        if (code != null) {
            for (TypeElement type : values()) {
                if (type.singulier.equals(code)) {
                    return type;
                }
            }
        }
        return POTEAU;
    }

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