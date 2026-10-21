package com.example.gkm_bar_fer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

import java.util.List;

public class EspaceClientActivity extends AppCompatActivity {

    private LinearLayout layoutClients;
    private TextView tvClientsVide;
    private ClientManager clientManager;
    // Recherche par nom
    private TextInputEditText etRecherche;
    private View layoutRechercheClient;
    private TextView tvResultatRecherche;
    private String requete = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_espace_client);

        configurerMenuNavigation();

        layoutClients = findViewById(R.id.layoutClients);
        tvClientsVide = findViewById(R.id.tvClientsVide);
        layoutRechercheClient = findViewById(R.id.layoutRechercheClient);
        etRecherche = findViewById(R.id.etRechercheClient);
        tvResultatRecherche = findViewById(R.id.tvResultatRecherche);
        Button btnAjouterClient = findViewById(R.id.btnAjouterClient);

        clientManager = ClientManager.getInstance(this);

        btnAjouterClient.setOnClickListener(v -> afficherDialogueAjoutClient());
        configurerRecherche();
        rafraichirAffichage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichirAffichage();
    }

    // ------------------------------------------------------------------
    // Header et menu latéral
    // ------------------------------------------------------------------

    private void configurerMenuNavigation() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Espace Client");
        }

        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.drawer_open,
                R.string.drawer_close
        );

        drawerLayout.addDrawerListener(drawerToggle);

        drawerToggle.getDrawerArrowDrawable().setColor(
                ContextCompat.getColor(this, R.color.white)
        );

        drawerToggle.syncState();
        navigationView.setCheckedItem(R.id.navEspaceClient);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navAccueil) {
                drawerLayout.closeDrawer(GravityCompat.START);

                Intent intent = new Intent(
                        EspaceClientActivity.this,
                        MenuActivity.class
                );

                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                startActivity(intent);
                finish();
                return true;
            }

            if (id == R.id.navStockDechets) {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(
                        EspaceClientActivity.this,
                        StockActivity.class
                ));
                return true;
            }

            if (id == R.id.navEspaceClient) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            if (id == R.id.navParametres) {
                drawerLayout.closeDrawer(GravityCompat.START);
                ParametresDialog.afficher(this);
                return true;
            }



            return false;
        });
    }

    // ------------------------------------------------------------------
    // Affichage de la liste des clients
    // ------------------------------------------------------------------

    private void rafraichirAffichage() {        layoutClients.removeAllViews();
        List<Client> tousLesClients = clientManager.getClients();

        if (tousLesClients.isEmpty()) {
            // Plus aucun client : on masque la recherche et on l'efface.
            layoutRechercheClient.setVisibility(View.GONE);
            tvClientsVide.setText("Aucun client pour l'instant.");
            tvClientsVide.setVisibility(View.VISIBLE);
            if (etRecherche.getText() != null && etRecherche.getText().length() > 0) {
                etRecherche.setText("");
            }
            return;
        }
        layoutRechercheClient.setVisibility(View.VISIBLE);

        List<Client> clients = filtrerClients(tousLesClients, requete);
        mettreAJourResultatRecherche(clients.size(), tousLesClients.size());

        if (clients.isEmpty()) {
            tvClientsVide.setText("Aucun client trouvé pour « " + requete + " ».");
            tvClientsVide.setVisibility(View.VISIBLE);
            return;
        }
        tvClientsVide.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Client client : clients) {
            View itemView = inflater.inflate(
                    R.layout.item_client,
                    layoutClients,
                    false
            );

            TextView tvNom = itemView.findViewById(R.id.tvNomClient);
            TextView tvVille = itemView.findViewById(R.id.tvVilleClient);
            Button btnConsulter = itemView.findViewById(R.id.btnConsulterClient);
            ImageButton btnSupprimer = itemView.findViewById(R.id.btnSupprimerClient);

            tvNom.setText(client.getNom());

            if (client.getVille().isEmpty()) {
                tvVille.setVisibility(View.GONE);
            } else {
                tvVille.setVisibility(View.VISIBLE);
                tvVille.setText(client.getVille());
            }

            btnConsulter.setOnClickListener(v -> {
                Intent intent = new Intent(
                        EspaceClientActivity.this,
                        CalculsClientActivity.class
                );
                intent.putExtra(CalculsClientActivity.EXTRA_CLIENT_ID, client.getId());
                startActivity(intent);
            });

            btnSupprimer.setOnClickListener(v -> afficherConfirmationSuppression(client));

            layoutClients.addView(itemView);
        }
    }

    // ------------------------------------------------------------------
    // Suppression d'un client (avec confirmation)
    // ------------------------------------------------------------------

    private void afficherConfirmationSuppression(final Client client) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer le client")
                .setMessage("Voulez-vous vraiment supprimer " + client.getNom()
                        + " et tous ses calculs enregistrés ? Cette action est définitive.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    CalculManager.getInstance(EspaceClientActivity.this)
                            .supprimerTousDuClient(client.getId());
                    clientManager.supprimer(client.getId());
                    Toast.makeText(
                            EspaceClientActivity.this,
                            "Client supprimé",
                            Toast.LENGTH_SHORT
                    ).show();
                    rafraichirAffichage();
                })
                .show();
    }

    // ------------------------------------------------------------------
    // Recherche d'un client par nom
    // ------------------------------------------------------------------

    private void configurerRecherche() {
        etRecherche.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                requete = s == null ? "" : s.toString().trim();
                rafraichirAffichage();
            }
        });

        // Touche "Rechercher" du clavier : on ferme simplement le clavier.
        etRecherche.setOnEditorActionListener((vue, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(vue.getWindowToken(), 0);
                }
                vue.clearFocus();
                return true;
            }
            return false;
        });
    }

    /** Garde les clients dont le nom contient le texte (sans tenir compte des majuscules ni des accents). */
    private List<Client> filtrerClients(List<Client> clients, String texte) {
        String recherche = normaliser(texte);
        if (recherche.isEmpty()) {
            return clients;
        }

        List<Client> resultat = new ArrayList<>();
        for (Client client : clients) {
            if (normaliser(client.getNom()).contains(recherche)) {
                resultat.add(client);
            }
        }
        return resultat;
    }

    private static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sansAccents.toLowerCase(Locale.ROOT).trim();
    }

    private void mettreAJourResultatRecherche(int trouves, int total) {
        if (requete.isEmpty()) {
            tvResultatRecherche.setText(total + (total > 1 ? " clients" : " client"));
        } else {
            tvResultatRecherche.setText(
                    trouves + (trouves > 1 ? " résultats" : " résultat") + " sur " + total);
        }
    }

    // ------------------------------------------------------------------
    // Modal "Ajouter Client"
    // ------------------------------------------------------------------

    private void afficherDialogueAjoutClient() {
        View vueDialogue = LayoutInflater.from(this).inflate(
                R.layout.dialog_ajout_client,
                null
        );

        final TextInputLayout layoutNom = vueDialogue.findViewById(R.id.layoutNomClient);
        final TextInputEditText etNom = vueDialogue.findViewById(R.id.etNomClient);
        final TextInputEditText etVille = vueDialogue.findViewById(R.id.etVilleClient);

        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setTitle("Ajouter un client")
                .setView(vueDialogue)
                .setNegativeButton("Ignorer", null)
                .setPositiveButton("Enregistrer", null)
                .create();

        // On redéfinit le clic sur "Enregistrer" pour que le modal reste ouvert
        // si le nom est vide.
        dialogue.setOnShowListener(d ->
                dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(bouton -> {
                    String nom = etNom.getText() == null
                            ? ""
                            : etNom.getText().toString().trim();
                    String ville = etVille.getText() == null
                            ? ""
                            : etVille.getText().toString().trim();

                    if (nom.isEmpty()) {
                        layoutNom.setError("Le nom du client est obligatoire");
                        return;
                    }

                    layoutNom.setError(null);
                    clientManager.ajouter(nom, ville);
                    Toast.makeText(
                            EspaceClientActivity.this,
                            "Client enregistré",
                            Toast.LENGTH_SHORT
                    ).show();
                    dialogue.dismiss();
                    etRecherche.setText("");
                    rafraichirAffichage();
                })
        );

        dialogue.show();
    }
}