package com.example.gkm_bar_fer.sauvegarde;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lien entre l'application (SharedPreferences réelles) et le format de sauvegarde.
 *
 * Étape 1 : LECTURE seulement. Cette classe ne modifie aucune donnée de l'application.
 * (L'écriture des données importées sera ajoutée à l'étape 5.)
 */
public final class SauvegardeDonnees {

    private SauvegardeDonnees() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Photographie des données métier : nom du fichier de prefs -> (clé -> valeur).
     * Contient clients, calculs enregistrés, stock global et un stock par client.
     * Ne contient JAMAIS la licence (le fichier "license_prefs" n'est pas dans la liste blanche).
     */
    public static Map<String, Map<String, ?>> instantane(Context contexte) {
        Context app = contexte.getApplicationContext();

        // Les 3 fichiers fixes + tous les "stock_client_<id>" présents sur le disque
        // (y compris ceux dont le client a été supprimé : l'import les validera).
        Set<String> noms = new TreeSet<>();
        noms.add(NomsPrefs.CLIENTS);
        noms.add(NomsPrefs.CALCULS);
        noms.add(NomsPrefs.STOCK_GLOBAL);

        File dossier = new File(app.getApplicationInfo().dataDir, "shared_prefs");
        File[] fichiers = dossier.listFiles();
        if (fichiers != null) {
            for (File fichier : fichiers) {
                String nomFichier = fichier.getName();
                if (nomFichier.endsWith(".xml")) {
                    String nom = nomFichier.substring(0, nomFichier.length() - 4);
                    if (NomsPrefs.estAutorise(nom)) {
                        noms.add(nom);
                    }
                }
            }
        }

        Map<String, Map<String, ?>> resultat = new LinkedHashMap<>();
        for (String nom : noms) {
            Map<String, ?> contenu = app.getSharedPreferences(nom, Context.MODE_PRIVATE).getAll();
            if (!contenu.isEmpty()) {          // un stock client vidé n'est pas sauvegardé
                resultat.put(nom, new HashMap<>(contenu));
            }
        }
        return resultat;
    }

    /** Crée le contenu d'un fichier .ferpro avec l'état actuel des données. */
    public static byte[] creer(Context contexte) {
        return SauvegardeCodec.ecrire(
                instantane(contexte), versionApp(contexte), System.currentTimeMillis());
    }

    static String versionApp(Context contexte) {
        try {
            PackageInfo info = contexte.getPackageManager()
                    .getPackageInfo(contexte.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
