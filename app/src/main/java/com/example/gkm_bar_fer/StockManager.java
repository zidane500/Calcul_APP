package com.example.gkm_bar_fer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gère le stock de chutes de fer, par type de barre (N6, N8, N12...).
 * Persistance : SharedPreferences, sérialisé en JSON — aucune dépendance externe
 * (org.json fait partie du SDK Android).
 *
 * Utilisation : StockManager.getInstance(context).ajouter("N12", 2.00, 5);
 */
public class StockManager {

    private static final String PREFS_NOM = "stock_dechets_prefs";
    private static final String CLE_STOCK = "stock_json";

    private static StockManager instance;

    private final SharedPreferences preferences;
    private final Map<String, List<MorceauFer>> stock;

    private StockManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NOM, Context.MODE_PRIVATE);
        stock = chargerDepuisPreferences();
    }

    public static synchronized StockManager getInstance(Context context) {
        if (instance == null) {
            instance = new StockManager(context);
        }
        return instance;
    }

    /** Retourne le stock actuel : type de fer -> liste de morceaux (longueur + quantité). */
    public Map<String, List<MorceauFer>> getStock() {
        return stock;
    }

    /**
     * Ajoute une quantité de morceaux d'une longueur donnée au stock d'un type de fer.
     * Si un morceau de cette longueur existe déjà pour ce type, la quantité s'additionne
     * au lieu de créer une nouvelle ligne.
     */
    public void ajouter(String typeFer, double longueur, int quantite) {
        if (quantite <= 0 || longueur <= 0) {
            return;
        }

        List<MorceauFer> morceaux = stock.get(typeFer);
        if (morceaux == null) {
            morceaux = new ArrayList<>();
            stock.put(typeFer, morceaux);
        }

        MorceauFer existant = trouverMorceau(morceaux, longueur);
        if (existant != null) {
            existant.quantite += quantite;
        } else {
            morceaux.add(new MorceauFer(longueur, quantite));
        }

        sauvegarder();
    }

    /**
     * Ajoute directement une liste de chutes issues d'un calcul au stock d'un type de fer.
     * Les chutes de même longueur sont automatiquement regroupées (ex: 3 chutes de 2,00 m
     * deviennent une seule ligne "2,00 m x3").
     */
    public void ajouterDechets(String typeFer, List<Double> dechets) {
        Map<Double, Integer> comptage = new LinkedHashMap<>();
        for (Double d : dechets) {
            if (d != null && d > 1e-9) {
                comptage.put(d, comptage.getOrDefault(d, 0) + 1);
            }
        }
        for (Map.Entry<Double, Integer> entry : comptage.entrySet()) {
            ajouter(typeFer, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Retire une quantité de morceaux d'une longueur donnée du stock d'un type de fer.
     * Si la quantité tombe à zéro (ou moins), la ligne est supprimée. Ne fait rien si le
     * type ou la longueur n'existent pas dans le stock (retrait déjà fait, ou stock vidé entre-temps).
     */
    public void retirer(String typeFer, double longueur, int quantite) {
        if (quantite <= 0 || longueur <= 0) {
            return;
        }

        List<MorceauFer> morceaux = stock.get(typeFer);
        if (morceaux == null) {
            return;
        }

        MorceauFer existant = trouverMorceau(morceaux, longueur);
        if (existant == null) {
            return;
        }

        existant.quantite -= quantite;
        if (existant.quantite <= 0) {
            morceaux.remove(existant);
        }
        if (morceaux.isEmpty()) {
            stock.remove(typeFer);
        }

        sauvegarder();
    }

    /**
     * Retire directement une liste de chutes du stock d'un type de fer — symétrique de
     * ajouterDechets(...), avec le même regroupement par longueur.
     */
    public void retirerDechets(String typeFer, List<Double> dechets) {
        Map<Double, Integer> comptage = new LinkedHashMap<>();
        for (Double d : dechets) {
            if (d != null && d > 1e-9) {
                comptage.put(d, comptage.getOrDefault(d, 0) + 1);
            }
        }
        for (Map.Entry<Double, Integer> entry : comptage.entrySet()) {
            retirer(typeFer, entry.getKey(), entry.getValue());
        }
    }

    private MorceauFer trouverMorceau(List<MorceauFer> morceaux, double longueur) {
        for (MorceauFer m : morceaux) {
            if (Math.abs(m.longueur - longueur) < 1e-6) {
                return m;
            }
        }
        return null;
    }

    private void sauvegarder() {
        try {
            JSONObject racine = new JSONObject();
            for (Map.Entry<String, List<MorceauFer>> entry : stock.entrySet()) {
                JSONArray tableau = new JSONArray();
                for (MorceauFer m : entry.getValue()) {
                    JSONObject objMorceau = new JSONObject();
                    objMorceau.put("longueur", m.longueur);
                    objMorceau.put("quantite", m.quantite);
                    tableau.put(objMorceau);
                }
                racine.put(entry.getKey(), tableau);
            }
            preferences.edit().putString(CLE_STOCK, racine.toString()).apply();
        } catch (JSONException e) {
            // Sérialisation impossible : le stock reste correct en mémoire pour cette session
        }
    }

    private Map<String, List<MorceauFer>> chargerDepuisPreferences() {
        Map<String, List<MorceauFer>> resultat = new LinkedHashMap<>();
        String json = preferences.getString(CLE_STOCK, null);
        if (json == null) {
            return resultat;
        }

        try {
            JSONObject racine = new JSONObject(json);
            Iterator<String> cles = racine.keys();
            while (cles.hasNext()) {
                String typeFer = cles.next();
                JSONArray tableau = racine.getJSONArray(typeFer);
                List<MorceauFer> morceaux = new ArrayList<>();
                for (int i = 0; i < tableau.length(); i++) {
                    JSONObject objMorceau = tableau.getJSONObject(i);
                    morceaux.add(new MorceauFer(
                            objMorceau.getDouble("longueur"),
                            objMorceau.getInt("quantite")));
                }
                resultat.put(typeFer, morceaux);
            }
        } catch (JSONException e) {
            // JSON corrompu : on repart d'un stock vide plutôt que de planter l'appli
        }

        return resultat;
    }
}