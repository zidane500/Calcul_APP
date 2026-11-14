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

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Restauration : le client choisit un fichier .ferpro, voit ce qu'il contient, confirme,
 * puis ses données sont remplacées par celles de la sauvegarde.
 *
 * Sécurités : le fichier est entièrement vérifié AVANT toute écriture (SauvegardeCodec),
 * une copie de sécurité des données actuelles est faite avant de remplacer, et en cas de
 * problème pendant l'écriture les données d'avant sont remises en place (MoteurRestauration).
 * La licence n'est jamais touchée.
 */
public class RestaurationActivity extends AppCompatActivity {

    private TextView tvMessage;
    private TextView tvApercu;
    private View carteApercu;
    private Button btnChoisir;
    private Button btnRestaurer;
    private ProgressBar progression;

    /** Contenu vérifié du fichier choisi (null tant qu'aucun fichier valide n'est choisi). */
    private SauvegardeCodec.Contenu contenuLu;

    private final ActivityResultLauncher<String[]> selecteur =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::fichierChoisi);

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
    // 1. Choix et vérification du fichier
    // ------------------------------------------------------------------

    private void fichierChoisi(final Uri uri) {
        if (uri == null) {
            return;                                   // le client a annulé
        }
        contenuLu = null;
        carteApercu.setVisibility(View.GONE);
        tvMessage.setVisibility(View.GONE);
        occupe(true);

        final Context app = getApplicationContext();
        SauvegardeAuto.TRAVAIL.execute(() -> {
            SauvegardeCodec.Contenu lu = null;
            int erreur = 0;
            try (InputStream flux = app.getContentResolver().openInputStream(uri)) {
                if (flux == null) {
                    erreur = R.string.restauration_err_illisible;
                } else {
                    lu = SauvegardeCodec.lire(flux);
                }
            } catch (SauvegardeCodec.InvalideException e) {
                erreur = messagePour(e.motif);
            } catch (IOException | RuntimeException e) {
                erreur = R.string.restauration_err_illisible;
            }

            final SauvegardeCodec.Contenu resultat = lu;
            final int texteErreur = erreur;
            runOnUiThread(() -> {
                occupe(false);
                if (resultat == null) {
                    afficherMessage(texteErreur);
                    return;
                }
                contenuLu = resultat;
                afficherApercu(resultat.apercu);
            });
        });
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

    private void afficherApercu(SauvegardeCodec.Apercu apercu) {
        Locale langue = getResources().getConfiguration().getLocales().get(0);
        String date = apercu.dateMs > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, langue)
                .format(new Date(apercu.dateMs))
                : "?";
        tvApercu.setText(getString(R.string.restauration_apercu, date,
                apercu.nbClients, apercu.nbCalculs, apercu.nbTypesStock, apercu.nbStocksClients));
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
                .setMessage(R.string.restauration_confirm_message)
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
                    afficherMessage(R.string.restauration_err_securite);
                } else if (fin == MoteurRestauration.Resultat.RESTAUREE) {
                    termine();
                } else if (fin == MoteurRestauration.Resultat.ECHEC_ANNULEE) {
                    afficherMessage(R.string.restauration_err_annulee);
                } else {
                    afficherMessage(R.string.restauration_err_incomplete);
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

    private void afficherMessage(int texte) {
        tvMessage.setText(texte);
        tvMessage.setVisibility(View.VISIBLE);
    }
}