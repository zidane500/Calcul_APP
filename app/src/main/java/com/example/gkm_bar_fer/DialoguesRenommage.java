package com.example.gkm_bar_fer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Boîtes de dialogue « Renommer » pour un calcul enregistré et pour un client. */
public final class DialoguesRenommage {

    private DialoguesRenommage() {
    }

    public static void renommerCalcul(Context contexte, CalculEnregistre calcul, Runnable apres) {
        View vue = LayoutInflater.from(contexte).inflate(R.layout.dialog_nom_calcul, null);
        TextInputLayout layoutNom = vue.findViewById(R.id.layoutNomCalcul);
        TextInputEditText etNom = vue.findViewById(R.id.etNomCalcul);
        etNom.setText(calcul.getNom());
        etNom.setSelection(etNom.length());

        AlertDialog dialogue = new AlertDialog.Builder(contexte)
                .setTitle(R.string.renommer_calcul)
                .setView(vue)
                .setNegativeButton(R.string.dialog_ignorer, null)
                .setPositiveButton(R.string.renommer, null)
                .create();

        dialogue.setOnShowListener(d ->
                dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(bouton -> {
                    String nom = etNom.getText() == null ? "" : etNom.getText().toString().trim();
                    if (nom.isEmpty()) {
                        layoutNom.setError(contexte.getString(R.string.renommer_nom_calcul_obligatoire));
                        return;
                    }
                    boolean ok = CalculManager.getInstance(contexte).renommer(calcul.getId(), nom);
                    Toast.makeText(contexte,
                            ok ? R.string.calcul_renomme : R.string.renommer_echec,
                            Toast.LENGTH_SHORT).show();
                    dialogue.dismiss();
                    if (ok && apres != null) {
                        apres.run();
                    }
                }));

        if (dialogue.getWindow() != null) {
            dialogue.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialogue.show();
    }

    public static void renommerClient(Context contexte, Client client, Runnable apres) {
        View vue = LayoutInflater.from(contexte).inflate(R.layout.dialog_ajout_client, null);
        TextInputLayout layoutNom = vue.findViewById(R.id.layoutNomClient);
        TextInputEditText etNom = vue.findViewById(R.id.etNomClient);
        TextInputEditText etVille = vue.findViewById(R.id.etVilleClient);
        etNom.setText(client.getNom());
        etVille.setText(client.getVille());
        etNom.setSelection(etNom.length());

        AlertDialog dialogue = new AlertDialog.Builder(contexte)
                .setTitle(R.string.renommer_client)
                .setView(vue)
                .setNegativeButton(R.string.dialog_ignorer, null)
                .setPositiveButton(R.string.renommer, null)
                .create();

        dialogue.setOnShowListener(d ->
                dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(bouton -> {
                    String nom = etNom.getText() == null ? "" : etNom.getText().toString().trim();
                    String ville = etVille.getText() == null ? "" : etVille.getText().toString().trim();
                    if (nom.isEmpty()) {
                        layoutNom.setError(contexte.getString(R.string.renommer_nom_client_obligatoire));
                        return;
                    }

                    ClientManager gestionnaire = ClientManager.getInstance(contexte);
                    String ancienNom = client.getNom().trim();

                    // L'historique du stock ne garde que le nom : on ne le modifie que si
                    // ce nom n'appartient qu'à ce client.
                    boolean ancienNomUnique = true;
                    for (Client autre : gestionnaire.getClients()) {
                        if (!autre.getId().equals(client.getId())
                                && autre.getNom().trim().equals(ancienNom)) {
                            ancienNomUnique = false;
                            break;
                        }
                    }

                    boolean ok = gestionnaire.renommer(client.getId(), nom, ville);
                    if (ok && ancienNomUnique && !ancienNom.equals(nom)) {
                        StockManager.renommerClientPartout(
                                contexte, gestionnaire.getClients(), ancienNom, nom);
                    }
                    Toast.makeText(contexte,
                            ok ? R.string.client_renomme : R.string.renommer_echec,
                            Toast.LENGTH_SHORT).show();
                    dialogue.dismiss();
                    if (ok && apres != null) {
                        apres.run();
                    }
                }));

        if (dialogue.getWindow() != null) {
            dialogue.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialogue.show();
    }
}