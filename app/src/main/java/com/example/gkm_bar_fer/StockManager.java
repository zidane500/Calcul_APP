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

    private static final String CLE_VERSION = "stock_version";
    private static final String PREFIXE_OPERATION = "stock_operation_";

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
     * Retourne une copie des morceaux disponibles pour un type de fer.
     * Modifier cette copie ne modifie pas le stock réel.
     */
    public List<MorceauFer> obtenirMorceaux(String typeFer) {
        List<MorceauFer> copie = new ArrayList<>();
        List<MorceauFer> morceaux = stock.get(typeFer);

        if (morceaux != null) {
            for (MorceauFer morceau : morceaux) {
                copie.add(new MorceauFer(
                        morceau.longueur,
                        morceau.quantite
                ));
            }
        }

        return copie;
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
            long nouvelleVersion = preferences.getLong(CLE_VERSION, 0L) + 1L;

            preferences.edit()
                    .putString(CLE_STOCK, racine.toString())
                    .putLong(CLE_VERSION, nouvelleVersion)
                    .apply();
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
    /**
     * Confirme une opération complète.
     *
     * origines : une longueur par pièce réellement consommée.
     * restes   : une longueur par reste à remettre en stock.
     *
     * L'identifiant doit rester le même pour une même opération.
     * Retourne false si l'opération ne peut pas être appliquée.
     */
    public synchronized boolean confirmerUtilisationStock(
            String operationId,
            String typeFer,
            List<Double> origines,
            List<Double> restes) {

        if (operationId == null || operationId.trim().isEmpty()
                || typeFer == null || typeFer.trim().isEmpty()
                || origines == null || origines.isEmpty()
                || restes == null) {
            return false;
        }

        String cleOperation = PREFIXE_OPERATION + operationId;

        // Un identifiant déjà enregistré ne peut pas être réutilisé.
        if (preferences.contains(cleOperation)) {
            return false;
        }

        try {
            JSONObject avant = convertirStockEnJson(stock);

            // Travail sur une copie indépendante.
            Map<String, List<MorceauFer>> copie =
                    convertirJsonEnStock(avant);

            List<MorceauFer> morceaux = copie.get(typeFer);
            if (morceaux == null) {
                return false;
            }

            double longueurRetiree = 0.0;
            double longueurAjoutee = 0.0;

            // Retirer toutes les origines AVANT d'ajouter les restes.
            // Un reste produit ne doit pas servir à valider une origine.
            for (Double longueur : origines) {
                if (!longueurValide(longueur)) {
                    return false;
                }

                MorceauFer morceau = trouverMorceau(morceaux, longueur);

                if (morceau == null || morceau.quantite < 1) {
                    return false;
                }

                morceau.quantite--;
                longueurRetiree += longueur;

                if (morceau.quantite == 0) {
                    morceaux.remove(morceau);
                }
            }

            for (Double longueur : restes) {
                if (!longueurValide(longueur)) {
                    return false;
                }

                longueurAjoutee += longueur;

                MorceauFer morceau = trouverMorceau(morceaux, longueur);

                if (morceau == null) {
                    morceaux.add(new MorceauFer(longueur, 1));
                } else {
                    if (morceau.quantite == Integer.MAX_VALUE) {
                        return false;
                    }
                    morceau.quantite++;
                }
            }

            if (Double.isInfinite(longueurRetiree)
                    || Double.isInfinite(longueurAjoutee)
                    || longueurAjoutee > longueurRetiree + 1e-6) {
                return false;
            }

            if (morceaux.isEmpty()) {
                copie.remove(typeFer);
            }

            long nouvelleVersion =
                    preferences.getLong(CLE_VERSION, 0L) + 1L;

            JSONObject operation = new JSONObject();
            operation.put("etat", "confirmee");
            operation.put("versionApres", nouvelleVersion);
            operation.put("stockAvant", avant);

            String stockApres = convertirStockEnJson(copie).toString();

            // Une seule écriture pour le stock et son opération.
            boolean enregistre = preferences.edit()
                    .putString(CLE_STOCK, stockApres)
                    .putLong(CLE_VERSION, nouvelleVersion)
                    .putString(cleOperation, operation.toString())
                    .commit();

            if (!enregistre) {
                // SharedPreferences peut avoir changé en mémoire :
                // invalider le reçu et restaurer le contenu précédent.
                preferences.edit()
                        .putString(CLE_STOCK, avant.toString())
                        .putLong(CLE_VERSION, nouvelleVersion - 1L)
                        .remove(cleOperation)
                        .commit();
                return false;
            }

            stock.clear();
            stock.putAll(copie);
            return true;

        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Annule une confirmation uniquement si aucun mouvement de stock
     * n'a eu lieu depuis. Restaure alors exactement l'état précédent.
     */
    public synchronized boolean annulerUtilisationStock(String operationId) {
        if (operationId == null || operationId.trim().isEmpty()) {
            return false;
        }

        String cleOperation = PREFIXE_OPERATION + operationId;
        String texteOperation = preferences.getString(cleOperation, null);

        if (texteOperation == null) {
            return false;
        }

        try {
            JSONObject operation = new JSONObject(texteOperation);

            if (!"confirmee".equals(operation.getString("etat"))) {
                return false;
            }

            long versionActuelle = preferences.getLong(CLE_VERSION, 0L);

            if (versionActuelle != operation.getLong("versionApres")) {
                return false;
            }

            JSONObject avant = operation.getJSONObject("stockAvant");
            Map<String, List<MorceauFer>> stockRestaure =
                    convertirJsonEnStock(avant);

            String stockActuel = convertirStockEnJson(stock).toString();

            operation.put("etat", "annulee");

            boolean enregistre = preferences.edit()
                    .putString(CLE_STOCK, avant.toString())
                    .putLong(CLE_VERSION, versionActuelle + 1L)
                    .putString(cleOperation, operation.toString())
                    .commit();

            if (!enregistre) {
                preferences.edit()
                        .putString(CLE_STOCK, stockActuel)
                        .putLong(CLE_VERSION, versionActuelle)
                        .putString(cleOperation, texteOperation)
                        .commit();
                return false;
            }

            stock.clear();
            stock.putAll(stockRestaure);
            return true;

        } catch (JSONException e) {
            return false;
        }
    }

    private boolean longueurValide(Double longueur) {
        return longueur != null
                && longueur > 0.0
                && !Double.isNaN(longueur)
                && !Double.isInfinite(longueur);
    }

    private JSONObject convertirStockEnJson(
            Map<String, List<MorceauFer>> source) throws JSONException {

        JSONObject racine = new JSONObject();

        for (Map.Entry<String, List<MorceauFer>> entree : source.entrySet()) {
            JSONArray tableau = new JSONArray();

            for (MorceauFer morceau : entree.getValue()) {
                JSONObject objet = new JSONObject();
                objet.put("longueur", morceau.longueur);
                objet.put("quantite", morceau.quantite);
                tableau.put(objet);
            }

            racine.put(entree.getKey(), tableau);
        }

        return racine;
    }

    private Map<String, List<MorceauFer>> convertirJsonEnStock(
            JSONObject racine) throws JSONException {

        Map<String, List<MorceauFer>> resultat = new LinkedHashMap<>();
        Iterator<String> cles = racine.keys();

        while (cles.hasNext()) {
            String typeFer = cles.next();
            JSONArray tableau = racine.getJSONArray(typeFer);
            List<MorceauFer> morceaux = new ArrayList<>();

            for (int i = 0; i < tableau.length(); i++) {
                JSONObject objet = tableau.getJSONObject(i);

                morceaux.add(new MorceauFer(
                        objet.getDouble("longueur"),
                        objet.getInt("quantite")
                ));
            }

            resultat.put(typeFer, morceaux);
        }

        return resultat;
    }
}