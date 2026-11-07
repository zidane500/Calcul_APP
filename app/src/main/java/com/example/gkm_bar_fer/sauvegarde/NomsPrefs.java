package com.example.gkm_bar_fer.sauvegarde;

import java.util.regex.Pattern;

/**
 * Noms des fichiers de préférences (et des clés) qui contiennent les données métier de Fer Pro.
 *
 * Source unique : ClientManager, CalculManager et StockManager utilisent ces constantes,
 * donc la sauvegarde ne peut pas se désynchroniser si un nom change un jour.
 *
 * Volontairement ABSENT : "license_prefs" (licence, Installation ID, horloge protégée).
 * La sauvegarde ne lit jamais ce fichier et l'import refuse de l'écrire.
 *
 * Classe 100 % Java (aucune dépendance Android) : testable sur PC.
 */
public final class NomsPrefs {

    private NomsPrefs() {
        // Classe utilitaire : pas d'instance.
    }

    // --- Clients -----------------------------------------------------------
    public static final String CLIENTS = "clients_prefs";
    public static final String CLE_CLIENTS = "clients_json";

    // --- Calculs enregistrés -----------------------------------------------
    public static final String CALCULS = "calculs_clients_prefs";
    public static final String CLE_CALCULS = "calculs_json";

    // --- Stock (global, et un par client) ----------------------------------
    public static final String STOCK_GLOBAL = "stock_dechets_prefs";
    public static final String PREFIXE_STOCK_CLIENT = "stock_client_";
    public static final String CLE_STOCK = "stock_json";
    public static final String CLE_VERSION = "stock_version";
    public static final String CLE_HISTORIQUE = "historique_json";
    public static final String PREFIXE_OPERATION = "stock_operation_";

    /** Identifiant de client : un UUID (lettres, chiffres, tirets), jamais de "/" ni de ".". */
    private static final Pattern ID_CLIENT = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /** @return true si ce fichier de préférences fait partie des données sauvegardables. */
    public static boolean estAutorise(String nom) {
        if (nom == null) {
            return false;
        }
        if (CLIENTS.equals(nom) || CALCULS.equals(nom) || STOCK_GLOBAL.equals(nom)) {
            return true;
        }
        return estStockClient(nom);
    }

    /** @return true pour "stock_client_<id valide>". */
    public static boolean estStockClient(String nom) {
        if (nom == null || !nom.startsWith(PREFIXE_STOCK_CLIENT)) {
            return false;
        }
        String id = nom.substring(PREFIXE_STOCK_CLIENT.length());
        return ID_CLIENT.matcher(id).matches();
    }
}
