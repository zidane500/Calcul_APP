package com.example.gkm_bar_fer;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Fenêtre "Paramètres" : choix de la langue de l'application, validé par un bouton "Confirmer".
 *
 * Pour ajouter une langue : une ligne dans LANGUES, un dossier res/values-xx/strings.xml
 * et une ligne <locale> dans res/xml/locales_config.xml.
 */
public final class ParametresDialog {

    private ParametresDialog() {
    }

    /** {code de langue, nom affiché dans sa propre langue}. */
    private static final String[][] LANGUES = {
            {"fr", "Français"},
            {"ar", "العربية"},
    };

    /** Depuis un écran sans saisie à perdre (accueil, stock, clients). */
    public static void afficher(Activity activite) {
        afficher(activite, false);
    }

    /**
     * @param ecranAvecSaisies true depuis l'écran de calcul : changer la langue le redémarre,
     *                         on affiche donc un avertissement.
     */
    public static void afficher(final Activity activite, boolean ecranAvecSaisies) {
        View vue = LayoutInflater.from(activite).inflate(R.layout.dialog_parametres, null);
        AutoCompleteTextView listeLangues = vue.findViewById(R.id.actvLangue);
        Button btnConfirmer = vue.findViewById(R.id.btnConfirmerLangue);
        TextView tvAvertissement = vue.findViewById(R.id.tvAvertissementLangue);

        tvAvertissement.setVisibility(ecranAvecSaisies ? View.VISIBLE : View.GONE);

        final String codeActuel = codeLangueActuel(activite);
        final int[] choix = {indexDe(codeActuel)};

        String[] noms = new String[LANGUES.length];
        for (int i = 0; i < LANGUES.length; i++) {
            noms[i] = LANGUES[i][1];
        }
        listeLangues.setAdapter(new ArrayAdapter<>(
                activite, android.R.layout.simple_dropdown_item_1line, noms));
        listeLangues.setText(noms[choix[0]], false);   // false : ne filtre pas la liste
        listeLangues.setOnItemClickListener((parent, v, position, id) -> choix[0] = position);

        final AlertDialog dialogue = new AlertDialog.Builder(activite)
                .setTitle(R.string.parametres_titre)
                .setView(vue)
                .create();

        btnConfirmer.setOnClickListener(v -> {
            String code = LANGUES[choix[0]][0];
            dialogue.dismiss();
            if (!code.equals(codeActuel)) {
                appliquerLangue(code);
            }
        });

        dialogue.show();
    }

    /** Langue réellement affichée par l'app ; "fr" si elle n'est pas dans la liste. */
    private static String codeLangueActuel(Activity activite) {
        String langue = activite.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return indexDe(langue) >= 0 ? langue : "fr";
    }

    private static int indexDe(String code) {
        for (int i = 0; i < LANGUES.length; i++) {
            if (LANGUES[i][0].equals(code)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Applique la langue : AppCompat la mémorise, recrée les écrans ouverts et, pour l'arabe,
     * passe l'affichage de droite à gauche. "ar-TN" garde les chiffres latins (0123).
     */
    private static void appliquerLangue(String code) {
        String etiquette = "ar".equals(code) ? "ar-TN" : code;
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(etiquette));
    }
}