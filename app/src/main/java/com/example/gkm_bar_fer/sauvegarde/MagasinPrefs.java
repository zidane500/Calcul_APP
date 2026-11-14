package com.example.gkm_bar_fer.sauvegarde;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Accès réel aux fichiers de préférences pour la restauration.
 * N'écrit que les fichiers de la liste blanche (NomsPrefs) : jamais la licence.
 */
final class MagasinPrefs implements MoteurRestauration.Magasin {

    private final Context app;

    MagasinPrefs(Context contexte) {
        this.app = contexte.getApplicationContext();
    }

    @Override
    public Map<String, ? extends Map<String, ?>> instantane() {
        return SauvegardeDonnees.instantane(app);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean ecrire(Map<String, Map<String, Object>> fichiers) {
        for (Map.Entry<String, Map<String, Object>> fichier : fichiers.entrySet()) {
            String nom = fichier.getKey();
            if (!NomsPrefs.estAutorise(nom)) {
                return false;
            }
            SharedPreferences.Editor editeur =
                    app.getSharedPreferences(nom, Context.MODE_PRIVATE).edit();
            editeur.clear();
            for (Map.Entry<String, Object> cle : fichier.getValue().entrySet()) {
                Object valeur = cle.getValue();
                if (valeur instanceof String) {
                    editeur.putString(cle.getKey(), (String) valeur);
                } else if (valeur instanceof Long) {
                    editeur.putLong(cle.getKey(), (Long) valeur);
                } else if (valeur instanceof Integer) {
                    editeur.putInt(cle.getKey(), (Integer) valeur);
                } else if (valeur instanceof Boolean) {
                    editeur.putBoolean(cle.getKey(), (Boolean) valeur);
                } else if (valeur instanceof Float) {
                    editeur.putFloat(cle.getKey(), (Float) valeur);
                } else if (valeur instanceof Set) {
                    editeur.putStringSet(cle.getKey(), new HashSet<>((Set<String>) valeur));
                } else {
                    return false;
                }
            }
            if (!editeur.commit()) {      // écriture synchrone : on sait si elle a réussi
                return false;
            }
        }
        return true;
    }
}