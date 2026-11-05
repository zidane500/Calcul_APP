package com.example.gkm_bar_fer.licence;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.gkm_bar_fer.R;

/**
 * Section "Licence" de la fenêtre Paramètres : état de la licence, date d'expiration,
 * Installation ID, et bouton pour saisir un nouveau code (renouvellement avant l'expiration).
 *
 * Lecture seule : cette section n'écrit rien (la saisie se fait dans ActivationActivity).
 */
public final class SectionLicence {

    /** Au-delà de ce nombre de jours restants, l'information n'est pas mise en alerte. */
    private static final int JOURS_ALERTE = 14;

    private SectionLicence() {
    }

    /**
     * @param vue            la vue de la fenêtre Paramètres (dialog_parametres.xml)
     * @param fermerFenetre  action qui ferme la fenêtre Paramètres
     */
    public static void lier(final Activity activite, View vue, final Runnable fermerFenetre) {
        LicenseManager licenseManager = LicenseManager.getInstance(activite);
        TextView tvEtat = vue.findViewById(R.id.tvLicenceEtat);
        TextView tvId = vue.findViewById(R.id.tvLicenceId);
        Button btnNouveauCode = vue.findViewById(R.id.btnNouveauCode);

        tvId.setText(activite.getString(R.string.licence_id_ligne,
                licenseManager.getInstallationId()));

        LicensePayload licence = licenseManager.getLicence();
        if (licenseManager.getStatus() == LicenseStatus.VALID && licence != null) {
            if (licence.estAVie()) {
                tvEtat.setText(R.string.licence_etat_vie);
            } else {
                int jours = licenseManager.getJoursRestants();
                tvEtat.setText(activite.getString(R.string.licence_etat_limitee,
                        LicenseDates.formater(licence.getJourFin()), jours));
                if (jours <= JOURS_ALERTE) {
                    tvEtat.setTextColor(ContextCompat.getColor(activite, R.color.retirer_text));
                }
            }
        } else {
            tvEtat.setText(R.string.licence_etat_aucune);
        }

        btnNouveauCode.setOnClickListener(v -> {
            fermerFenetre.run();
            Intent intent = new Intent(activite, ActivationActivity.class);
            intent.putExtra(ActivationActivity.EXTRA_DEPUIS_PARAMETRES, true);
            activite.startActivity(intent);
        });
    }
}