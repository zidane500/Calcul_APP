package com.example.gkm_bar_fer.sauvegarde;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Restauration : remplace les données actuelles par le contenu d'une sauvegarde déjà VÉRIFIÉE
 * (SauvegardeCodec.lire). Tout ou rien : si l'écriture échoue ou si la relecture ne correspond
 * pas à la sauvegarde, les données d'avant sont remises en place.
 *
 * Classe 100 % Java (aucune dépendance Android) : testable sur PC.
 */
public final class MoteurRestauration {

    private MoteurRestauration() {
    }

    /** Accès aux fichiers de préférences (côté Android : MagasinPrefs). */
    public interface Magasin {
        /** Données actuelles (fichiers de données uniquement, jamais la licence). */
        Map<String, ? extends Map<String, ?>> instantane();

        /**
         * Écrit chaque fichier : contenu = EXACTEMENT ce map (un map vide = fichier vidé).
         * Écriture synchrone. @return false si une écriture a échoué.
         */
        boolean ecrire(Map<String, Map<String, Object>> fichiers);
    }

    public enum Resultat {
        /** Données restaurées et relues avec succès. */
        RESTAUREE,
        /** Échec : les données d'avant ont été remises en place. */
        ECHEC_ANNULEE,
        /** Échec ET impossible de remettre les données d'avant (très rare). */
        ECHEC_INCOMPLETE
    }

    public static Resultat restaurer(SauvegardeCodec.Contenu contenu, Magasin magasin) {
        Map<String, Map<String, Object>> avant = copie(magasin.instantane());
        Map<String, Map<String, Object>> voulu = copie(contenu.prefs);

        // Les fichiers présents maintenant mais absents de la sauvegarde sont vidés.
        Set<String> noms = new TreeSet<>(avant.keySet());
        noms.addAll(voulu.keySet());

        Map<String, Map<String, Object>> aEcrire = new LinkedHashMap<>();
        for (String nom : noms) {
            Map<String, Object> cles = voulu.get(nom);
            aEcrire.put(nom, cles == null ? new LinkedHashMap<String, Object>() : cles);
        }

        boolean ecrit;
        try {
            ecrit = magasin.ecrire(aEcrire);
        } catch (RuntimeException e) {
            ecrit = false;
        }

        if (ecrit && memeContenu(copie(magasin.instantane()), voulu)) {
            return Resultat.RESTAUREE;
        }
        return annuler(magasin, avant, noms);
    }

    private static Resultat annuler(Magasin magasin, Map<String, Map<String, Object>> avant,
                                    Set<String> noms) {
        Map<String, Map<String, Object>> retour = new LinkedHashMap<>();
        for (String nom : noms) {
            Map<String, Object> cles = avant.get(nom);
            retour.put(nom, cles == null ? new LinkedHashMap<String, Object>() : cles);
        }
        try {
            if (magasin.ecrire(retour) && memeContenu(copie(magasin.instantane()), avant)) {
                return Resultat.ECHEC_ANNULEE;
            }
        } catch (RuntimeException e) {
            // tombe dans ECHEC_INCOMPLETE
        }
        return Resultat.ECHEC_INCOMPLETE;
    }

    /** Copie sans les fichiers vides (un fichier vide et un fichier absent sont équivalents). */
    private static Map<String, Map<String, Object>> copie(Map<String, ? extends Map<String, ?>> source) {
        Map<String, Map<String, Object>> resultat = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Map<String, ?>> e : source.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            resultat.put(e.getKey(), new LinkedHashMap<String, Object>(e.getValue()));
        }
        return resultat;
    }

    private static boolean memeContenu(Map<String, Map<String, Object>> a,
                                       Map<String, Map<String, Object>> b) {
        return SauvegardeCodec.empreinte(a).equals(SauvegardeCodec.empreinte(b));
    }
}