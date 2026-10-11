package com.example.gkm_bar_fer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gère la liste des clients.
 * Persistance : SharedPreferences sérialisé en JSON (même principe que StockManager).
 */
public class ClientManager {

    private static final String PREFS_NOM = "clients_prefs";
    private static final String CLE_CLIENTS = "clients_json";

    private static ClientManager instance;

    private final SharedPreferences preferences;
    private final List<Client> clients;

    private ClientManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NOM, Context.MODE_PRIVATE);
        clients = charger();
    }

    public static synchronized ClientManager getInstance(Context context) {
        if (instance == null) {
            instance = new ClientManager(context);
        }
        return instance;
    }

    /** Retourne une copie de la liste (le plus récent en premier). */
    public List<Client> getClients() {
        return new ArrayList<>(clients);
    }

    /** Retourne le client correspondant à l'identifiant, ou null s'il n'existe pas. */
    public Client trouverParId(String idClient) {
        for (Client c : clients) {
            if (c.getId().equals(idClient)) {
                return c;
            }
        }
        return null;
    }

    /** Ajoute un client. Le nom est obligatoire, la ville est optionnelle. */
    public Client ajouter(String nom, String ville) {
        Client client = new Client(
                UUID.randomUUID().toString(),
                nom.trim(),
                ville == null ? "" : ville.trim(),
                System.currentTimeMillis()
        );
        clients.add(0, client);
        sauvegarder();
        return client;
    }

    /** Supprime un client à partir de son identifiant. */
    public boolean supprimer(String idClient) {
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).getId().equals(idClient)) {
                clients.remove(i);
                sauvegarder();
                return true;
            }
        }
        return false;
    }

    private void sauvegarder() {
        JSONArray tableau = new JSONArray();
        try {
            for (Client c : clients) {
                JSONObject objet = new JSONObject();
                objet.put("id", c.getId());
                objet.put("nom", c.getNom());
                objet.put("ville", c.getVille());
                objet.put("date", c.getDateCreation());
                tableau.put(objet);
            }
        } catch (JSONException e) {
            return;
        }
        preferences.edit().putString(CLE_CLIENTS, tableau.toString()).apply();
    }

    private List<Client> charger() {
        List<Client> liste = new ArrayList<>();
        String json = preferences.getString(CLE_CLIENTS, null);
        if (json == null) {
            return liste;
        }
        try {
            JSONArray tableau = new JSONArray(json);
            for (int i = 0; i < tableau.length(); i++) {
                JSONObject objet = tableau.getJSONObject(i);
                liste.add(new Client(
                        objet.getString("id"),
                        objet.getString("nom"),
                        objet.optString("ville", ""),
                        objet.optLong("date", 0L)
                ));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return liste;
    }
}