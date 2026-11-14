package com.example.gkm_bar_fer.sauvegarde;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.gkm_bar_fer.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Section "Sauvegarde" de la fenêtre Paramètres : date de la dernière sauvegarde,
 * bouton "Sauvegarder maintenant" et bouton "Envoyer une copie".
 * Le travail se fait sur le thread de SauvegardeAuto (jamais en même temps qu'une sauvegarde auto).
 */
public final class SectionSauvegarde {

    private static final String DOSSIER_ENVOIS = "envois";

    private SectionSauvegarde() {
    }

    public static void lier(final Activity activite, View vue, final Runnable fermer) {
        final TextView tvDate = vue.findViewById(R.id.tvDerniereSauvegarde);
        final Button btnMaintenant = vue.findViewById(R.id.btnSauvegarderMaintenant);
        final Button btnCopie = vue.findViewById(R.id.btnEnvoyerCopie);

        afficherDate(activite, tvDate);

        Button btnRestaurer = vue.findViewById(R.id.btnRestaurer);
        btnRestaurer.setOnClickListener(v -> {
            fermer.run();
            activite.startActivity(new Intent(activite, RestaurationActivity.class));
        });

        btnMaintenant.setOnClickListener(v -> {
            btnMaintenant.setEnabled(false);
            SauvegardeAuto.TRAVAIL.execute(() -> {
                final MoteurSauvegarde.Resultat resultat = SauvegardeAuto.executer();
                SauvegardeAuto.HANDLER.post(() -> {
                    btnMaintenant.setEnabled(true);
                    if (activite.isFinishing() || activite.isDestroyed()) {
                        return;
                    }
                    afficherDate(activite, tvDate);
                    Toast.makeText(activite, message(resultat), Toast.LENGTH_LONG).show();
                });
            });
        });

        btnCopie.setOnClickListener(v -> {
            btnCopie.setEnabled(false);
            SauvegardeAuto.TRAVAIL.execute(() -> {
                final MoteurSauvegarde.Resultat resultat = SauvegardeAuto.executer();
                File copie = null;
                if (resultat == MoteurSauvegarde.Resultat.REUSSIE
                        || resultat == MoteurSauvegarde.Resultat.INCHANGEE) {
                    copie = copierDerniereVersion(activite);
                }
                final File aEnvoyer = copie;
                SauvegardeAuto.HANDLER.post(() -> {
                    btnCopie.setEnabled(true);
                    if (activite.isFinishing() || activite.isDestroyed()) {
                        return;
                    }
                    afficherDate(activite, tvDate);
                    if (aEnvoyer == null) {
                        int texte = (resultat == MoteurSauvegarde.Resultat.REUSSIE
                                || resultat == MoteurSauvegarde.Resultat.INCHANGEE)
                                ? R.string.sauvegarde_envoi_impossible
                                : message(resultat);
                        Toast.makeText(activite, texte, Toast.LENGTH_LONG).show();
                        return;
                    }
                    partager(activite, aEnvoyer);
                });
            });
        });
    }

    private static int message(MoteurSauvegarde.Resultat resultat) {
        if (resultat == null) {
            return R.string.sauvegarde_permission;
        }
        switch (resultat) {
            case REUSSIE:
                return R.string.sauvegarde_ok;
            case INCHANGEE:
                return R.string.sauvegarde_inchangee;
            case VIDE:
                return R.string.sauvegarde_vide;
            default:
                return R.string.sauvegarde_echec;
        }
    }

    private static void afficherDate(Context ctx, TextView tv) {
        long date = new SauvegardeAuto.MemoirePrefs(
                ctx.getSharedPreferences(SauvegardeAuto.PREFS_MEMOIRE, Context.MODE_PRIVATE))
                .derniereDate();
        if (date <= 0L) {
            tv.setText(R.string.sauvegarde_aucune);
            return;
        }
        Locale langue = ctx.getResources().getConfiguration().getLocales().get(0);
        String texte = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, langue)
                .format(new Date(date));
        tv.setText(ctx.getString(R.string.sauvegarde_derniere, texte));
    }

    /** Copie la dernière sauvegarde dans le cache (partageable). null en cas de problème. */
    private static File copierDerniereVersion(Context ctx) {
        try {
            Destination destination = SauvegardeAuto.creerDestination(ctx);
            if (destination == null) {
                return null;
            }
            List<Destination.Entree> entrees = destination.lister();   // du plus récent au plus ancien
            if (entrees.isEmpty()) {
                return null;
            }

            String voulu = new SauvegardeAuto.MemoirePrefs(
                    ctx.getSharedPreferences(SauvegardeAuto.PREFS_MEMOIRE, Context.MODE_PRIVATE))
                    .dernierNom();
            Destination.Entree choisie = entrees.get(0);
            for (Destination.Entree entree : entrees) {
                if (entree.nom.equals(voulu)) {
                    choisie = entree;
                    break;
                }
            }

            File dossier = new File(ctx.getCacheDir(), DOSSIER_ENVOIS);
            if (!dossier.isDirectory() && !dossier.mkdirs()) {
                return null;
            }
            File[] anciens = dossier.listFiles();
            if (anciens != null) {
                for (File ancien : anciens) {
                    //noinspection ResultOfMethodCallIgnored
                    ancien.delete();
                }
            }

            File copie = new File(dossier, choisie.nom);
            try (InputStream entree = destination.ouvrir(choisie);
                 FileOutputStream sortie = new FileOutputStream(copie)) {
                byte[] tampon = new byte[8192];
                int lu;
                while ((lu = entree.read(tampon)) > 0) {
                    sortie.write(tampon, 0, lu);
                }
            }
            return copie;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void partager(Activity activite, File fichier) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    activite, activite.getPackageName() + ".fileprovider", fichier);
            Intent envoi = new Intent(Intent.ACTION_SEND);
            envoi.setType("application/octet-stream");
            envoi.putExtra(Intent.EXTRA_STREAM, uri);
            envoi.putExtra(Intent.EXTRA_SUBJECT, fichier.getName());
            envoi.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activite.startActivity(Intent.createChooser(
                    envoi, activite.getString(R.string.sauvegarde_partage_titre)));
        } catch (IllegalArgumentException | ActivityNotFoundException e) {
            Toast.makeText(activite, R.string.sauvegarde_envoi_impossible, Toast.LENGTH_LONG).show();
        }
    }
}