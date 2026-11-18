package com.example.gkm_bar_fer.sauvegarde;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gkm_bar_fer.CalculManager;
import com.example.gkm_bar_fer.ClientManager;
import com.example.gkm_bar_fer.MenuActivity;
import com.example.gkm_bar_fer.R;
import com.example.gkm_bar_fer.StockManager;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Restauration : le client choisit UN ou PLUSIEURS fichiers .ferpro, voit ce qu'ils contiennent,
 * confirme, puis ses données sont remplacées.
 *  - 1 fichier   : restauration simple.
 *  - 2 fichiers ou plus : fusion (MoteurFusion), puis restauration du résultat.
 *
 * Sécurités : chaque fichier est entièrement vérifié AVANT toute écriture (SauvegardeCodec), le
 * résultat de la fusion repasse par la même vérification, une copie de sécurité des données
 * actuelles est faite avant de remplacer, et en cas de problème pendant l'écriture les données
 * d'avant sont remises en place (MoteurRestauration). La licence n'est jamais touchée.
 */
public class RestaurationActivity extends AppCompatActivity {

    /** Au-delà, on refuse : évite de charger trop de données en mémoire. */
    private static final int MAX_FICHIERS = 20;

    private TextView tvMessage;
    private TextView tvApercu;
    private View carteApercu;
    private Button btnChoisir;
    private Button btnRestaurer;
    private ProgressBar progression;

    /** Données prêtes à restaurer (un fichier, ou le résultat de la fusion). null = rien de choisi. */
    private SauvegardeCodec.Contenu contenuLu;
    /** Nombre de sauvegardes derrière contenuLu (1 = restauration simple, 2+ = fusion). */
    private int nbSauvegardes;

    private final ActivityResultLauncher<String[]> selecteur =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),
                    this::fichiersChoisis);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restauration);

        tvMessage = findViewById(R.id.tvMessageRestauration);
        tvApercu = findViewById(R.id.tvApercuRestauration);
        carteApercu = findViewById(R.id.carteApercuRestauration);
        btnChoisir = findViewById(R.id.btnChoisirFichier);
        btnRestaurer = findViewById(R.id.btnConfirmerRestauration);
        progression = findViewById(R.id.progressionRestauration);

        btnChoisir.setOnClickListener(v -> selecteur.launch(new String[]{"*/*"}));
        btnRestaurer.setOnClickListener(v -> demanderConfirmation());
    }

    // ------------------------------------------------------------------
    // 1. Choix et vérification des fichiers
    // ------------------------------------------------------------------

    private void fichiersChoisis(final List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;                                   // le client a annulé
        }
        contenuLu = null;
        nbSauvegardes = 0;
        carteApercu.setVisibility(View.GONE);
        tvMessage.setVisibility(View.GONE);

        if (uris.size() > MAX_FICHIERS) {
            afficherMessage(getString(R.string.restauration_err_trop, MAX_FICHIERS));
            return;
        }
        occupe(true);

        final Context app = getApplicationContext();
        SauvegardeAuto.TRAVAIL.execute(() -> {
            List<SauvegardeCodec.Contenu> valides = new ArrayList<>();
            int ignores = 0;
            int premiereErreur = 0;

            for (Uri uri : uris) {
                try (InputStream flux = app.getContentResolver().openInputStream(uri)) {
                    if (flux == null) {
                        throw new IOException("flux null");
                    }
                    valides.add(SauvegardeCodec.lire(flux));
                } catch (SauvegardeCodec.InvalideException e) {
                    ignores++;
                    if (premiereErreur == 0) {
                        premiereErreur = messagePour(e.motif);
                    }
                } catch (IOException | RuntimeException e) {
                    ignores++;
                    if (premiereErreur == 0) {
                        premiereErreur = R.string.restauration_err_illisible;
                    }
                }
            }

            // Un seul fichier valide : restauration simple. Plusieurs : fusion.
            SauvegardeCodec.Contenu pret = null;
            int erreur = premiereErreur;
            if (valides.size() == 1) {
                pret = valides.get(0);
            } else if (valides.size() > 1) {
                try {
                    pret = fusionner(valides);
                } catch (JSONException | SauvegardeCodec.InvalideException | RuntimeException e) {
                    erreur = R.string.restauration_err_fusion;
                }
            }
            if (pret == null && erreur == 0) {
                erreur = R.string.restauration_err_illisible;
            }

            final SauvegardeCodec.Contenu resultat = pret;
            final int nb = valides.size();
            final int nbIgnores = ignores;
            final int texteErreur = erreur;
            final long plusAncienne = plusAncienneDate(valides);
            final long plusRecente = plusRecenteDate(valides);

            runOnUiThread(() -> {
                occupe(false);
                if (resultat == null) {
                    afficherMessage(getString(texteErreur));
                    return;
                }
                contenuLu = resultat;
                nbSauvegardes = nb;
                afficherApercu(resultat.apercu, nb, plusAncienne, plusRecente);
                if (nbIgnores > 0) {
                    afficherMessage(getString(R.string.restauration_info_ignores, nbIgnores));
                }
            });
        });
    }

    /** Fusionne puis refait passer le résultat par la vérification complète du codec. */
    private static SauvegardeCodec.Contenu fusionner(List<SauvegardeCodec.Contenu> valides)
            throws JSONException, SauvegardeCodec.InvalideException {
        java.util.Map<String, java.util.Map<String, Object>> fusion = MoteurFusion.fusionner(valides);
        SauvegardeCodec.Apercu recent = valides.get(0).apercu;
        for (SauvegardeCodec.Contenu c : valides) {
            if (c.apercu.dateMs > recent.dateMs) {
                recent = c.apercu;
            }
        }
        return SauvegardeCodec.lire(SauvegardeCodec.ecrire(fusion, recent.versionApp, recent.dateMs));
    }

    private static long plusAncienneDate(List<SauvegardeCodec.Contenu> liste) {
        long min = Long.MAX_VALUE;
        for (SauvegardeCodec.Contenu c : liste) {
            min = Math.min(min, c.apercu.dateMs);
        }
        return liste.isEmpty() ? 0L : min;
    }

    private static long plusRecenteDate(List<SauvegardeCodec.Contenu> liste) {
        long max = 0L;
        for (SauvegardeCodec.Contenu c : liste) {
            max = Math.max(max, c.apercu.dateMs);
        }
        return max;
    }

    private static int messagePour(SauvegardeCodec.Motif motif) {
        switch (motif) {
            case FORMAT_INCONNU:
                return R.string.restauration_err_format;
            case VERSION_TROP_RECENTE:
                return R.string.restauration_err_version;
            case FICHIER_TROP_GRAND:
                return R.string.restauration_err_taille;
            case SOMME_DE_CONTROLE:
                return R.string.restauration_err_abime;
            case CONTENU_INTERDIT:
            case CONTENU_INVALIDE:
                return R.string.restauration_err_contenu;
            default:
                return R.string.restauration_err_illisible;
        }
    }

    private String formaterDate(long dateMs) {
        if (dateMs <= 0) {
            return "?";
        }
        Locale langue = getResources().getConfiguration().getLocales().get(0);
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, langue)
                .format(new Date(dateMs));
    }

    private void afficherApercu(SauvegardeCodec.Apercu apercu, int nb, long plusAncienne, long plusRecente) {
        if (nb > 1) {
            tvApercu.setText(getString(R.string.restauration_apercu_fusion, nb,
                    formaterDate(plusAncienne), formaterDate(plusRecente),
                    apercu.nbClients, apercu.nbCalculs, apercu.nbTypesStock, apercu.nbStocksClients));
        } else {
            tvApercu.setText(getString(R.string.restauration_apercu, formaterDate(apercu.dateMs),
                    apercu.nbClients, apercu.nbCalculs, apercu.nbTypesStock, apercu.nbStocksClients));
        }
        carteApercu.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // 2. Confirmation puis restauration
    // ------------------------------------------------------------------

    private void demanderConfirmation() {
        if (contenuLu == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.restauration_confirm_titre)
                .setMessage(nbSauvegardes > 1
                        ? R.string.restauration_confirm_message_fusion
                        : R.string.restauration_confirm_message)
                .setPositiveButton(R.string.restauration_confirm_oui, (d, w) -> restaurer())
                .setNegativeButton(R.string.restauration_confirm_non, null)
                .show();
    }

    private void restaurer() {
        final SauvegardeCodec.Contenu aRestaurer = contenuLu;
        if (aRestaurer == null) {
            return;
        }
        occupe(true);
        final Context app = getApplicationContext();

        SauvegardeAuto.TRAVAIL.execute(() -> {
            // a) Copie de sécurité des données ACTUELLES (si elles existent).
            MoteurSauvegarde.Resultat secu = SauvegardeAuto.executer();
            boolean securise = secu == MoteurSauvegarde.Resultat.REUSSIE
                    || secu == MoteurSauvegarde.Resultat.INCHANGEE
                    || secu == MoteurSauvegarde.Resultat.VIDE
                    || (secu == null && SauvegardeDonnees.instantane(app).isEmpty());

            // b) Remplacement (tout ou rien).
            MoteurRestauration.Resultat resultat = null;
            if (securise) {
                resultat = MoteurRestauration.restaurer(aRestaurer, new MagasinPrefs(app));
            }

            final boolean copieOk = securise;
            final MoteurRestauration.Resultat fin = resultat;
            runOnUiThread(() -> {
                occupe(false);
                if (!copieOk) {
                    afficherMessage(getString(R.string.restauration_err_securite));
                } else if (fin == MoteurRestauration.Resultat.RESTAUREE) {
                    termine();
                } else if (fin == MoteurRestauration.Resultat.ECHEC_ANNULEE) {
                    afficherMessage(getString(R.string.restauration_err_annulee));
                } else {
                    afficherMessage(getString(R.string.restauration_err_incomplete));
                }
            });
        });
    }

    /** Les gestionnaires gardent une copie en mémoire : on l'oublie, puis on repart du menu. */
    private void termine() {
        ClientManager.reinitialiser();
        CalculManager.reinitialiser();
        StockManager.reinitialiser();

        Toast.makeText(this, R.string.restauration_ok, Toast.LENGTH_LONG).show();
        Intent menu = new Intent(this, MenuActivity.class);
        menu.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(menu);
        finish();
    }

    // ------------------------------------------------------------------
    // Affichage
    // ------------------------------------------------------------------

    private void occupe(boolean enCours) {
        progression.setVisibility(enCours ? View.VISIBLE : View.GONE);
        btnChoisir.setEnabled(!enCours);
        btnRestaurer.setEnabled(!enCours);
    }

    private void afficherMessage(String texte) {
        tvMessage.setText(texte);
        tvMessage.setVisibility(View.VISIBLE);
    }
}