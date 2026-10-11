package com.example.gkm_bar_fer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Un calcul enregistré pour un client.
 * Les données (entrées + résultats) sont stockées sous forme de JSON,
 * produit et relu par MainActivity.
 */
public class CalculEnregistre {

    private final String id;
    private final String clientId;
    private final String nom;
    private final long dateCreation;
    private final long dateModification;
    private final String donneesJson;

    public CalculEnregistre(String id, String clientId, String nom,
                            long dateCreation, long dateModification,
                            String donneesJson) {
        this.id = id;
        this.clientId = clientId;
        this.nom = nom;
        this.dateCreation = dateCreation;
        this.dateModification = dateModification;
        this.donneesJson = donneesJson;
    }

    public String getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getNom() {
        return nom;
    }

    public long getDateCreation() {
        return dateCreation;
    }

    public long getDateModification() {
        return dateModification;
    }

    public String getDonneesJson() {
        return donneesJson;
    }

    /** Type d'élément du calcul (poteau par défaut pour les anciens calculs). */
    public TypeElement getTypeElement() {
        try {
            return TypeElement.depuisCode(
                    new JSONObject(donneesJson).optString("typeElement", null));
        } catch (JSONException e) {
            return TypeElement.POTEAU;
        }
    }
}