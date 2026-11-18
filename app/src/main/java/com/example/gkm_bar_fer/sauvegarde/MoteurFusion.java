package com.example.gkm_bar_fer.sauvegarde;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fusion de plusieurs sauvegardes (déjà vérifiées par SauvegardeCodec.lire) en un seul jeu de données.
 *
 * Chaque sauvegarde est une photo COMPLÈTE de l'application à un instant. Règles :
 *  - Clients : réunion de tous les clients (même id = la version de la sauvegarde la plus récente).
 *  - Calculs : réunion de tous les calculs (même id = celui modifié en dernier).
 *  - Stock (global et par client) : chaque fichier de stock est pris TEL QUEL dans la sauvegarde
 *    la plus récente qui le contient. Jamais d'addition : ce serait compter deux fois les mêmes chutes.
 *
 * Classe 100 % Java (aucune dépendance Android) : testable sur PC.
 */
public final class MoteurFusion {

    private MoteurFusion() {
    }

    /**
     * @param sauvegardes au moins une sauvegarde vérifiée
     * @return les données fusionnées (même forme que Contenu.prefs)
     * @throws JSONException si la liste des clients ou des calculs d'une sauvegarde est inutilisable
     */
    public static Map<String, Map<String, Object>> fusionner(List<SauvegardeCodec.Contenu> sauvegardes)
            throws JSONException {
        if (sauvegardes == null || sauvegardes.isEmpty()) {
            throw new IllegalArgumentException("Aucune sauvegarde à fusionner");
        }

        // La plus récente d'abord (tri stable : à date égale, l'ordre de choix est conservé).
        List<SauvegardeCodec.Contenu> recentes = new ArrayList<>(sauvegardes);
        Collections.sort(recentes, (a, b) -> Long.compare(b.apercu.dateMs, a.apercu.dateMs));

        Map<String, Map<String, Object>> resultat = new LinkedHashMap<>();

        // 1. Fichiers de stock : le plus récent qui contient le fichier l'emporte, en entier.
        for (SauvegardeCodec.Contenu sauvegarde : recentes) {
            for (Map.Entry<String, Map<String, Object>> fichier : sauvegarde.prefs.entrySet()) {
                String nom = fichier.getKey();
                if (NomsPrefs.CLIENTS.equals(nom) || NomsPrefs.CALCULS.equals(nom)) {
                    continue;
                }
                if (!resultat.containsKey(nom) && !fichier.getValue().isEmpty()) {
                    resultat.put(nom, new LinkedHashMap<String, Object>(fichier.getValue()));
                }
            }
        }

        // 2. Clients : réunion par id, la version la plus récente l'emporte, récents en premier.
        reunir(recentes, resultat, NomsPrefs.CLIENTS, NomsPrefs.CLE_CLIENTS, "date", null);

        // 3. Calculs : réunion par id, le dernier modifié l'emporte, récents en premier.
        reunir(recentes, resultat, NomsPrefs.CALCULS, NomsPrefs.CLE_CALCULS,
                "dateModification", "dateModification");

        return resultat;
    }

    /**
     * @param champTri      champ de date qui ordonne la liste finale (récent en premier)
     * @param champConflit  si non null : à id égal, l'élément avec la plus grande valeur de ce champ
     *                      l'emporte ; sinon la sauvegarde la plus récente l'emporte
     */
    private static void reunir(List<SauvegardeCodec.Contenu> recentes,
                               Map<String, Map<String, Object>> resultat,
                               String nomPrefs, String cle, String champTri, String champConflit)
            throws JSONException {
        Map<String, JSONObject> parId = new LinkedHashMap<>();

        for (SauvegardeCodec.Contenu sauvegarde : recentes) {
            Map<String, Object> fichier = sauvegarde.prefs.get(nomPrefs);
            if (fichier == null || !(fichier.get(cle) instanceof String)) {
                continue;
            }
            JSONArray tableau = new JSONArray((String) fichier.get(cle));
            for (int i = 0; i < tableau.length(); i++) {
                JSONObject objet = tableau.getJSONObject(i);
                String id = objet.getString("id");
                JSONObject existant = parId.get(id);
                if (existant == null) {
                    parId.put(id, objet);
                } else if (champConflit != null
                        && objet.optLong(champConflit, 0L) > existant.optLong(champConflit, 0L)) {
                    parId.put(id, objet);
                }
            }
        }
        if (parId.isEmpty()) {
            return;
        }

        List<JSONObject> liste = new ArrayList<>(parId.values());
        final String tri = champTri;
        Collections.sort(liste, (a, b) -> Long.compare(b.optLong(tri, 0L), a.optLong(tri, 0L)));

        JSONArray sortie = new JSONArray();
        for (JSONObject objet : liste) {
            sortie.put(objet);
        }
        Map<String, Object> fichierSortie = new LinkedHashMap<>();
        fichierSortie.put(cle, sortie.toString());
        resultat.put(nomPrefs, fichierSortie);
    }
}