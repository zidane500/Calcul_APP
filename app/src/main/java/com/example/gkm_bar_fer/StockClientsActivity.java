package com.example.gkm_bar_fer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Liste des clients pour le menu « Stock client ».
 * Un clic sur un client ouvre son stock (StockActivity en mode client).
 */
public class StockClientsActivity extends AppCompatActivity {

    private LinearLayout layoutClients;
    private TextView tvClientsVide;
    private ClientManager clientManager;

    private TextInputEditText etRecherche;
    private View layoutRechercheClient;
    private TextView tvResultatRecherche;
    private String requete = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_clients);

        configurerMenuNavigation();

        layoutClients = findViewById(R.id.layoutClients);
        tvClientsVide = findViewById(R.id.tvClientsVide);
        layoutRechercheClient = findViewById(R.id.layoutRechercheClient);
        etRecherche = findViewById(R.id.etRechercheClient);
        tvResultatRecherche = findViewById(R.id.tvResultatRecherche);

        clientManager = ClientManager.getInstance(this);

        configurerRecherche();
        rafraichirAffichage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Le stock d'un client a pu changer pendant qu'on était dans son écran.
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
            getSupportActionBar().setTitle("Stock client");
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
        navigationView.setCheckedItem(R.id.navStockClient);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navAccueil) {
                drawerLayout.closeDrawer(GravityCompat.START);

                Intent intent = new Intent(
                        StockClientsActivity.this,
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

            if (id == R.id.navEspaceClient) {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(
                        StockClientsActivity.this,
                        EspaceClientActivity.class
                ));
                finish();
                return true;
            }

            if (id == R.id.navStockDechets) {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(
                        StockClientsActivity.this,
                        StockActivity.class
                ));
                finish();
                return true;
            }

            if (id == R.id.navStockClient) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            if (id == R.id.navParametres) {
                drawerLayout.closeDrawer(GravityCompat.START);
                ParametresDialog.afficher(StockClientsActivity.this);
                return true;
            }

            return false;
        });
    }

    // ------------------------------------------------------------------
    // Affichage de la liste des clients
    // ------------------------------------------------------------------

    private void rafraichirAffichage() {
        layoutClients.removeAllViews();
        List<Client> tousLesClients = clientManager.getClients();

        if (tousLesClients.isEmpty()) {
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
                    R.layout.item_client_stock,
                    layoutClients,
                    false
            );

            TextView tvNom = itemView.findViewById(R.id.tvNomClientStock);
            TextView tvVille = itemView.findViewById(R.id.tvVilleClientStock);
            TextView tvResume = itemView.findViewById(R.id.tvResumeStockClient);
            TextView tvInitiale = itemView.findViewById(R.id.tvInitialeClientStock);
            MaterialCardView cardAvatar = itemView.findViewById(R.id.cardAvatarClientStock);

            tvNom.setText(client.getNom());
            tvInitiale.setText(initialeDe(client.getNom()));
            cardAvatar.setCardBackgroundColor(couleurAvatar(client.getId()));

            if (client.getVille().isEmpty()) {
                tvVille.setVisibility(View.GONE);
            } else {
                tvVille.setVisibility(View.VISIBLE);
                tvVille.setText(client.getVille());
            }

            tvResume.setText(resumeStock(client.getId()));

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(
                        StockClientsActivity.this,
                        StockActivity.class
                );
                intent.putExtra(StockActivity.EXTRA_CLIENT_ID, client.getId());
                startActivity(intent);
            });

            layoutClients.addView(itemView);
        }
    }

    /** « Stock vide » ou « 56 pièces – 285,50 m » pour le stock de ce client. */
    private String resumeStock(String idClient) {
        Map<String, List<MorceauFer>> stock =
                StockManager.getInstanceClient(this, idClient).getStock();

        int pieces = 0;
        double metres = 0.0;
        for (List<MorceauFer> morceaux : stock.values()) {
            for (MorceauFer m : morceaux) {
                pieces += m.quantite;
                metres += m.longueur * m.quantite;
            }
        }

        if (pieces == 0) {
            return "Stock vide";
        }
        return String.format(Locale.FRANCE, "%d %s – %.2f m",
                pieces, pieces > 1 ? "pièces" : "pièce", metres);
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

    private static final int[] COULEURS_AVATAR = {
            Color.parseColor("#173B65"),
            Color.parseColor("#3E6B63"),
            Color.parseColor("#5B3E8E"),
            Color.parseColor("#2E4A62"),
            Color.parseColor("#54413A")
    };

    private int couleurAvatar(String clientId) {
        return COULEURS_AVATAR[Math.abs(clientId.hashCode()) % COULEURS_AVATAR.length];
    }

    private String initialeDe(String nom) {
        String propre = nom == null ? "" : nom.trim();
        if (propre.isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(propre.codePointAt(0))).toUpperCase(Locale.getDefault());
    }
}