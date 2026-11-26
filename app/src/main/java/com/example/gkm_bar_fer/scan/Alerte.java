package com.example.gkm_bar_fer.scan;

/** Remarque sur une lecture : information, point à vérifier, ou contrôle échoué. */
public final class Alerte {

    public enum Niveau { INFO, ATTENTION, ERREUR }

    public final Niveau niveau;
    public final String message;

    public Alerte(Niveau niveau, String message) {
        this.niveau = niveau;
        this.message = message;
    }

    @Override
    public String toString() {
        return niveau + " : " + message;
    }
}