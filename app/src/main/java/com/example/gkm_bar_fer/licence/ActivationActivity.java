package com.example.gkm_bar_fer.licence;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gkm_bar_fer.MenuActivity;
import com.example.gkm_bar_fer.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Écran de licence : affiche l'Installation ID du téléphone et permet de saisir (ou coller)
 * le code fourni par le vendeur. Le même écran sert à :
 *   - ACTIVER (aucune licence),
 *   - RENOUVELER (licence expirée : la date d'expiration est affichée),
 *   - remplacer une licence invalide, ou signaler une date de téléphone incorrecte.
 *
 * Il ne lit et n'écrit que la licence (LicenseManager) : aucune donnée métier.
 * L'expiration ne supprime jamais les clients, calculs, stock ou historique.
 */
public class ActivationActivity extends AppCompatActivity {

    private LicenseManager licenseManager;
    private String installationId;
    private TextInputLayout layoutCode;
    private TextInputEditText etCode;
    private boolean renouvellement; // true quand la licence a expiré

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        licenseManager = LicenseManager.getInstance(this);
        installationId = licenseManager.getInstallationId();

        TextView tvInstallationId = findViewById(R.id.tvInstallationId);
        tvInstallationId.setText(installationId);

        layoutCode = findViewById(R.id.layoutCode);
        etCode = findViewById(R.id.etCode);
        // Dès que le client modifie le code, on efface l'ancien message d'erreur.
        etCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                layoutCode.setError(null);
            }
        });

        configurerSelonStatut(licenseManager.getStatus());

        findViewById(R.id.btnCopierId).setOnClickListener(v -> copierInstallationId());
        findViewById(R.id.btnPartagerId).setOnClickListener(v -> partagerInstallationId());
        findViewById(R.id.btnColler).setOnClickListener(v -> collerCode());
        findViewById(R.id.btnActiver).setOnClickListener(v -> activer());

        // Retour : on quitte l'application (impossible d'entrer sans licence valide).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Exemple : la date du téléphone vient d'être corrigée -> la licence redevient valide.
        if (licenseManager.getStatus().autoriseAcces()) {
            ouvrirApplication();
        }
    }

    /** Adapte les textes à la situation : activation, licence expirée, invalide, date incorrecte. */
    private void configurerSelonStatut(LicenseStatus statut) {
        TextView tvTitre = findViewById(R.id.tvTitre);
        TextView tvStatut = findViewById(R.id.tvStatut);
        View cardStatut = findViewById(R.id.cardStatut);
        TextView tvLabelCode = findViewById(R.id.tvLabelCode);
        Button btnActiver = findViewById(R.id.btnActiver);
        TextView tvContact = findViewById(R.id.tvContact);
        String telephone = LicenseConstants.SUPPORT_PHONE;

        // Par défaut : première activation (textes déjà dans le layout).
        tvContact.setText(getString(R.string.licence_contact, telephone));

        LicensePayload licence = licenseManager.getLicence();
        if (statut == LicenseStatus.EXPIRED && licence != null) {
            renouvellement = true;
            tvTitre.setText(R.string.licence_titre_expiree);
            tvStatut.setText(getString(R.string.licence_expiree_message,
                    LicenseDates.formater(licence.getJourFin()))
                    + "\n\n" + getString(R.string.licence_donnees_conservees));
            cardStatut.setVisibility(View.VISIBLE);
            tvLabelCode.setText(R.string.licence_nouveau_code);
            btnActiver.setText(R.string.licence_renouveler);
            tvContact.setText(getString(R.string.licence_contact_renouveler, telephone));
        } else if (statut == LicenseStatus.CLOCK_ERROR) {
            tvTitre.setText(R.string.licence_titre_date);
            tvStatut.setText(R.string.licence_date_message);
            cardStatut.setVisibility(View.VISIBLE);
        } else if (statut == LicenseStatus.INVALID || statut == LicenseStatus.EXPIRED) {
            tvTitre.setText(R.string.licence_titre_invalide);
            tvStatut.setText(getString(R.string.licence_invalide_message)
                    + "\n\n" + getString(R.string.licence_donnees_conservees));
            cardStatut.setVisibility(View.VISIBLE);
            tvLabelCode.setText(R.string.licence_nouveau_code);
        }
    }

    private void copierInstallationId() {
        ClipboardManager presse = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (presse != null) {
            presse.setPrimaryClip(ClipData.newPlainText("Installation ID", installationId));
            Toast.makeText(this, R.string.licence_id_copie, Toast.LENGTH_SHORT).show();
        }
    }

    private void partagerInstallationId() {
        Intent envoi = new Intent(Intent.ACTION_SEND);
        envoi.setType("text/plain");
        envoi.putExtra(Intent.EXTRA_TEXT, getString(R.string.licence_partage_texte, installationId));
        startActivity(Intent.createChooser(envoi, getString(R.string.licence_partager_titre)));
    }

    private void collerCode() {
        ClipboardManager presse = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = presse == null ? null : presse.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, R.string.licence_presse_papiers_vide, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence texte = clip.getItemAt(0).coerceToText(this);
        if (texte == null || texte.toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.licence_presse_papiers_vide, Toast.LENGTH_SHORT).show();
            return;
        }
        etCode.setText(texte.toString().trim());
        etCode.setSelection(etCode.getText() == null ? 0 : etCode.getText().length());
    }

    private void activer() {
        cacherClavier();
        String code = etCode.getText() == null ? "" : etCode.getText().toString().trim();
        if (code.isEmpty()) {
            layoutCode.setError(getString(R.string.licence_erreur_vide));
            return;
        }

        LicenseManager.ActivationResult resultat = licenseManager.activer(code);
        if (resultat.estSucces()) {
            LicensePayload licence = resultat.getLicence();
            String message;
            if (licence.estAVie()) {
                message = getString(renouvellement
                        ? R.string.licence_renouvelee_vie : R.string.licence_succes_vie);
            } else {
                message = getString(renouvellement
                                ? R.string.licence_renouvelee_limitee : R.string.licence_succes_limitee,
                        LicenseDates.formater(licence.getJourFin()));
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            ouvrirApplication();
        } else {
            layoutCode.setError(messageErreur(resultat));
        }
    }

    private String messageErreur(LicenseManager.ActivationResult resultat) {
        switch (resultat.getEtat()) {
            case CONFIGURATION_MANQUANTE:
                return getString(R.string.licence_erreur_config);
            case ERREUR_STOCKAGE:
                return getString(R.string.licence_erreur_stockage);
            case DATE_INCORRECTE:
                return getString(R.string.licence_date_message);
            default:
                break;
        }
        LicenseVerifier.Resultat raison = resultat.getResultat();
        if (raison == null) {
            return getString(R.string.licence_erreur_invalide);
        }
        switch (raison) {
            case APPAREIL_INCORRECT:
                return getString(R.string.licence_erreur_appareil);
            case EXPIREE:
                return getString(R.string.licence_erreur_expiree);
            case PAS_ENCORE_VALIDE:
                return getString(R.string.licence_erreur_pas_encore,
                        LicenseDates.formater(resultat.getLicence().getJourDebut()));
            case PRODUIT_INCORRECT:
                return getString(R.string.licence_erreur_produit);
            default:
                return getString(R.string.licence_erreur_invalide);
        }
    }

    /** Licence valide : on ouvre l'application normalement (menu principal). */
    private void ouvrirApplication() {
        Intent intent = new Intent(this, MenuActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void cacherClavier() {
        InputMethodManager clavier =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (clavier != null && focus != null) {
            clavier.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}