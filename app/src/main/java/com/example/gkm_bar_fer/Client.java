package com.example.gkm_bar_fer;

/**
 * Un client de l'Espace Client : nom (obligatoire) et ville (optionnelle).
 */
public class Client {

    private final String id;
    private final String nom;
    private final String ville;
    private final long dateCreation;

    public Client(String id, String nom, String ville, long dateCreation) {
        this.id = id;
        this.nom = nom;
        this.ville = ville;
        this.dateCreation = dateCreation;
    }

    public String getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getVille() {
        return ville;
    }

    public long getDateCreation() {
        return dateCreation;
    }
}