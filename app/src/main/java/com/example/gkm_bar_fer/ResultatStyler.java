package com.example.gkm_bar_fer;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Met en forme le texte des résultats (titres en gras et en couleur, libellés grisés, valeurs en
 * gras, retraits...) sans changer les chaînes de calcul : le texte brut reste celui des
 * fichiers de textes, seule la présentation à l'écran change. Fonctionne en français et en arabe.
 */
public final class ResultatStyler {

    private static final String PUCE = "\u2022";
    private static final String RLM = "\u200F"; // marque droite-à-gauche (arabe)

    // === Poteaux de 8,00 m (1 poteau) ===
    private static final Pattern ENTETE =
            Pattern.compile("^===\\s*(.+?)\\s*===$");
    // • N12:
    private static final Pattern TITRE_TYPE =
            Pattern.compile("^" + PUCE + "\\s*(.+?)[\\s" + RLM + "]*:\\s*$");
    //   • N12: 4 coupes/poteau -> 4 coupes au total
    private static final Pattern BESOIN =
            Pattern.compile("^\\s+" + PUCE + "\\s*(\\S+?)([\\s" + RLM + "]*):\\s*(.*)$");
    //   - Barres à acheter: 5
    private static final Pattern LIGNE =
            Pattern.compile("^\\s*-\\s*([^:]+?)([\\s" + RLM + "]*):\\s*(.*)$");
    //     * Chute: 2,00 m
    private static final Pattern SOUS_LIGNE =
            Pattern.compile("^\\s*\\*\\s*(.*)$");

    private ResultatStyler() {
    }

    /** Résultat normal : titres, besoins par élément, synthèse par type de barre. */
    public static CharSequence appliquer(Context ctx, String texte) {
        if (texte == null || texte.isEmpty()) {
            return "";
        }

        int couleurTitre = ContextCompat.getColor(ctx, R.color.primary_dark);
        int couleurTexte = ContextCompat.getColor(ctx, R.color.barre_type_text);
        int couleurLibelle = ContextCompat.getColor(ctx, R.color.secondary_text);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lignes = texte.split("\n", -1);

        for (int i = 0; i < lignes.length; i++) {
            String ligne = lignes[i];
            boolean derniere = (i == lignes.length - 1);
            int debut = sb.length();

            if (ligne.trim().isEmpty()) {
                // Ligne vide : on la raccourcit pour aérer sans créer de grands vides
                sb.append("\n");
                sb.setSpan(new RelativeSizeSpan(0.5f), debut, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            Matcher m;
            int retrait = 0;

            if ((m = ENTETE.matcher(ligne.trim())).matches()) {
                // Titre de section : gras, bleu, un peu plus grand
                sb.append(m.group(1));
                gras(sb, debut, sb.length());
                couleur(sb, debut, sb.length(), couleurTitre);
                taille(sb, debut, sb.length(), 1.08f);

            } else if ((m = TITRE_TYPE.matcher(ligne.trim())).matches()
                    && !ligne.startsWith(" ")) {
                // Type de barre (N12...) : grand et en gras
                sb.append(m.group(1));
                gras(sb, debut, sb.length());
                couleur(sb, debut, sb.length(), couleurTitre);
                taille(sb, debut, sb.length(), 1.2f);

            } else if ((m = BESOIN.matcher(ligne)).matches()) {
                // • N12: 4 coupes/poteau -> 4 coupes au total  (total en gras)
                sb.append(PUCE).append(" ");
                int debutType = sb.length();
                sb.append(m.group(1));
                gras(sb, debutType, sb.length());
                couleur(sb, debutType, sb.length(), couleurTitre);

                // On garde le séparateur d'origine (en arabe : marque droite-à-gauche) pour que
                // le diamètre reste avant le nombre de coupes.
                sb.append(m.group(2)).append(": ");
                String reste = m.group(3);
                int debutReste = sb.length();
                sb.append(reste);
                couleur(sb, debutReste, sb.length(), couleurTexte);

                int marque = reste.lastIndexOf("->");
                int tailleMarque = 2;
                if (marque < 0) {
                    marque = reste.lastIndexOf("=");
                    tailleMarque = 1;
                }
                if (marque >= 0) {
                    int debutTotal = debutReste + marque + tailleMarque;
                    gras(sb, debutTotal, sb.length());
                }
                retrait = 8;

            } else if ((m = LIGNE.matcher(ligne)).matches()) {
                // - Barres à acheter: 5  (libellé grisé, valeur en gras)
                int debutLibelle = sb.length();
                sb.append(m.group(1)).append(m.group(2)).append(": ");
                couleur(sb, debutLibelle, sb.length(), couleurLibelle);

                int debutValeur = sb.length();
                sb.append(m.group(3));
                gras(sb, debutValeur, sb.length());
                couleur(sb, debutValeur, sb.length(), couleurTexte);
                retrait = 12;

            } else if ((m = SOUS_LIGNE.matcher(ligne)).matches()) {
                // * Chute: 2,00 m  (sous-élément plus discret, plus décalé)
                sb.append("\u00B7 ").append(m.group(1));
                couleur(sb, debut, sb.length(), couleurLibelle);
                retrait = 28;

            } else {
                sb.append(ligne.trim());
                couleur(sb, debut, sb.length(), couleurTexte);
            }

            if (!derniere) {
                sb.append("\n");
            }
            if (retrait > 0) {
                int px = (int) (retrait * ctx.getResources().getDisplayMetrics().density);
                sb.setSpan(new LeadingMarginSpan.Standard(px), debut, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return sb;
    }

    /** Message d'erreur de saisie : en gras et en rouge. */
    public static CharSequence erreur(Context ctx, String texte) {
        SpannableStringBuilder sb = new SpannableStringBuilder(texte);
        gras(sb, 0, sb.length());
        couleur(sb, 0, sb.length(), ContextCompat.getColor(ctx, R.color.error));
        return sb;
    }

    private static void gras(SpannableStringBuilder sb, int from, int to) {
        sb.setSpan(new StyleSpan(Typeface.BOLD), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void couleur(SpannableStringBuilder sb, int from, int to, int couleur) {
        sb.setSpan(new ForegroundColorSpan(couleur), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void taille(SpannableStringBuilder sb, int from, int to, float facteur) {
        sb.setSpan(new RelativeSizeSpan(facteur), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}