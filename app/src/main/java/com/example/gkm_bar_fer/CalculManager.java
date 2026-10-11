package com.example.gkm_bar_fer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Gère les calculs enregistrés, rattachés à un client.
 * Persistance : SharedPreferences sérialisé en JSON (même principe que ClientManager).
 */
public class CalculManager {

    private static final String PREFS_NOM = "calculs_clients_prefs";
    private static final String CLE_CALCULS = "calculs_json";

    private static CalculManager instance;

    private final SharedPreferences preferences;
    private final List<CalculEnregistre> calculs;

    private CalculManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NOM, Context.MODE_PRIVATE);
        calculs = charger();
    }

    public static synchronized CalculManager getInstance(Context context) {
        if (instance == null) {
            instance = new CalculManager(context);
        }
        return instance;
    }

    /** Calculs d'un client, le plus récent en premier. */
    /** Calculs d'un client, le plus récemment modifié en premier. */
    public List<CalculEnregistre> getCalculsDuClient(String clientId) {
        List<CalculEnregistre> liste = new ArrayList<>();
        for (CalculEnregistre c : calculs) {
            if (c.getClientId().equals(clientId)) {
                liste.add(c);
            }
        }
        Collections.sort(liste, (a, b) ->
                Long.compare(b.getDateModification(), a.getDateModification()));
        return liste;
    }

    /** Retourne le calcul correspondant à l'identifiant, ou null s'il n'existe pas. */
    public CalculEnregistre trouverParId(String idCalcul) {
        for (CalculEnregistre c : calculs) {
            if (c.getId().equals(idCalcul)) {
                return c;
            }
        }
        return null;
    }

    /** Met à jour le nom et les données d'un calcul existant. */
    public boolean modifier(String idCalcul, String nouveauNom, String nouvellesDonnees) {
        for (int i = 0; i < calculs.size(); i++) {
            CalculEnregistre ancien = calculs.get(i);
            if (ancien.getId().equals(idCalcul)) {
                CalculEnregistre nouveau = new CalculEnregistre(
                        ancien.getId(),
                        ancien.getClientId(),
                        nouveauNom.trim(),
                        ancien.getDateCreation(),
                        System.currentTimeMillis(),
                        nouvellesDonnees
                );
                calculs.set(i, nouveau);
                if (!sauvegarder()) {
                    calculs.set(i, ancien);
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    /** Ajoute un calcul pour un client et retourne l'élément créé (ou null si échec). */
    public CalculEnregistre ajouter(String clientId, String nom, String donneesJson) {
        long maintenant = System.currentTimeMillis();
        CalculEnregistre calcul = new CalculEnregistre(
                UUID.randomUUID().toString(),
                clientId,
                nom.trim(),
                maintenant,
                maintenant,
                donneesJson
        );
        calculs.add(0, calcul);
        if (!sauvegarder()) {
            calculs.remove(0);
            return null;
        }
        return calcul;
    }

    /** Supprime tous les calculs d'un client (utilisé quand le client est supprimé). */
    public void supprimerTousDuClient(String clientId) {
        boolean modifie = false;
        for (int i = calculs.size() - 1; i >= 0; i--) {
            if (calculs.get(i).getClientId().equals(clientId)) {
                calculs.remove(i);
                modifie = true;
            }
        }
        if (modifie) {
            sauvegarder();
        }
    }

    private boolean sauvegarder() {
        JSONArray tableau = new JSONArray();
        try {
            for (CalculEnregistre c : calculs) {
                JSONObject objet = new JSONObject();
                objet.put("id", c.getId());
                objet.put("clientId", c.getClientId());
                objet.put("nom", c.getNom());
                objet.put("dateCreation", c.getDateCreation());
                objet.put("dateModification", c.getDateModification());
                objet.put("donnees", c.getDonneesJson());
                tableau.put(objet);
            }
        } catch (JSONException e) {
            return false;
        }
        return preferences.edit().putString(CLE_CALCULS, tableau.toString()).commit();
    }

    private List<CalculEnregistre> charger() {
        List<CalculEnregistre> liste = new ArrayList<>();
        String json = preferences.getString(CLE_CALCULS, null);
        if (json == null) {
            return liste;
        }
        try {
            JSONArray tableau = new JSONArray(json);
            for (int i = 0; i < tableau.length(); i++) {
                JSONObject objet = tableau.getJSONObject(i);
                liste.add(new CalculEnregistre(
                        objet.getString("id"),
                        objet.getString("clientId"),
                        objet.optString("nom", ""),
                        objet.optLong("dateCreation", 0L),
                        objet.optLong("dateModification", 0L),
                        objet.optString("donnees", "")
                ));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return liste;
    }
}