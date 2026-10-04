package com.example.gkm_bar_fer;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StockActivity extends AppCompatActivity {

    private LinearLayout layoutStock;
    private TextView tvStockVide;
    private StockManager stockManager;
    private String typeFerSelectionne;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock);

        configurerMenuNavigation();

        layoutStock = findViewById(R.id.layoutStock);
        tvStockVide = findViewById(R.id.tvStockVide);
        Button btnAjouterFer = findViewById(R.id.btnAjouterFer);

        stockManager = StockManager.getInstance(this);

        btnAjouterFer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                afficherDialogueChoixTypeEtUnite();
            }
        });

        rafraichirAffichageStock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichirAffichageStock();
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
            getSupportActionBar().setTitle("Stock Déchets");
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
        navigationView.setCheckedItem(R.id.navStockDechets);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navAccueil) {
                drawerLayout.closeDrawer(GravityCompat.START);

                Intent intent = new Intent(
                        StockActivity.this,
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
                return true;
            }

            return false;
        });
    }

    // ------------------------------------------------------------------
    // Affichage du stock
    // ------------------------------------------------------------------

    /**
     * Reconstruit entièrement l'affichage du stock à partir des données actuelles.
     */
    private void rafraichirAffichageStock() {
        layoutStock.removeAllViews();
        Map<String, List<MorceauFer>> stock = stockManager.getStock();

        if (stock.isEmpty()) {
            tvStockVide.setVisibility(View.VISIBLE);
            return;
        }
        tvStockVide.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, List<MorceauFer>> entry : stock.entrySet()) {
            String typeFer = entry.getKey();
            List<MorceauFer> morceaux = entry.getValue();

            View itemView = inflater.inflate(
                    R.layout.item_stock_type,
                    layoutStock,
                    false
            );

            TextView tvType = itemView.findViewById(R.id.tvTypeFerStock);
            TextView tvDetail = itemView.findViewById(R.id.tvDetailStock);

            tvType.setText(typeFer);
            tvDetail.setText(formatterDetailStock(morceaux));

            layoutStock.addView(itemView);
        }
    }

    /**
     * Met en forme les morceaux d'un type de fer avec un total récapitulatif.
     */
    private String formatterDetailStock(List<MorceauFer> morceaux) {
        StringBuilder texte = new StringBuilder();
        int totalPieces = 0;
        double totalMetres = 0;

        for (MorceauFer m : morceaux) {
            texte.append(m.quantite)
                    .append(m.quantite > 1 ? " morceaux de " : " morceau de ")
                    .append(String.format(Locale.FRANCE, "%.2f", m.longueur))
                    .append(" m\n");

            totalPieces += m.quantite;
            totalMetres += m.quantite * m.longueur;
        }

        texte.append("\nTotal : ")
                .append(totalPieces)
                .append(totalPieces > 1 ? " pièces" : " pièce")
                .append(" — ")
                .append(String.format(Locale.FRANCE, "%.2f", totalMetres))
                .append(" m");

        return texte.toString();
    }

    // ------------------------------------------------------------------
    // Boîte de dialogue 1 : choix du type de fer, puis de l'unité
    // ------------------------------------------------------------------

    private void afficherDialogueChoixTypeEtUnite() {
        typeFerSelectionne = null;

        LayoutInflater inflater = LayoutInflater.from(this);
        View vueDialogue = inflater.inflate(
                R.layout.dialog_choix_type_unite,
                null
        );

        final Button btnChoisirType =
                vueDialogue.findViewById(R.id.btnChoisirType);
        Button btnChoisirUnite =
                vueDialogue.findViewById(R.id.btnChoisirUnite);

        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setView(vueDialogue)
                .setNegativeButton("Annuler", null)
                .create();

        btnChoisirType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                afficherListeChoixType(btnChoisirType);
            }
        });

        btnChoisirUnite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (typeFerSelectionne == null) {
                    Toast.makeText(
                            StockActivity.this,
                            "Choisissez d'abord un type de fer.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                dialogue.dismiss();
                afficherListeChoixUnite();
            }
        });

        dialogue.show();
    }

    private void afficherListeChoixType(final Button btnChoisirType) {
        final String[] types =
                ConfigurationBarres.listerTypes().toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Type de fer")
                .setItems(types, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        typeFerSelectionne = types[which];
                        btnChoisirType.setText(typeFerSelectionne);
                    }
                })
                .show();
    }

    private void afficherListeChoixUnite() {
        final String[] unites = {"Bar", "Coupes"};

        new AlertDialog.Builder(this)
                .setTitle("Unité")
                .setItems(unites, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            afficherDialogueSaisieBar(typeFerSelectionne);
                        } else {
                            afficherDialogueSaisieCoupes(typeFerSelectionne);
                        }
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------
    // Boîte de dialogue 2 : saisie de barres entières
    // ------------------------------------------------------------------

    private void afficherDialogueSaisieBar(final String typeFer) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View vueDialogue = inflater.inflate(
                R.layout.dialog_saisie_stock,
                null
        );

        TextView tvType = vueDialogue.findViewById(R.id.tvTypeFerDialog);
        TextView tvUnite = vueDialogue.findViewById(R.id.tvUniteDialog);

        final TextInputLayout layoutQuantite =
                vueDialogue.findViewById(R.id.layoutQuantite);
        final TextInputEditText etQuantite =
                vueDialogue.findViewById(R.id.etQuantite);
        TextInputLayout layoutLongueur =
                vueDialogue.findViewById(R.id.layoutLongueur);

        tvType.setText("Type : " + typeFer);
        tvUnite.setText(
                "Unité : Bar (barres entières de "
                        + String.format(
                        Locale.FRANCE,
                        "%.2f",
                        ConfigurationBarres.LONGUEUR_BARRE_STANDARD
                )
                        + " m)"
        );

        layoutQuantite.setHint("Nombre de barres");
        layoutLongueur.setVisibility(View.GONE);

        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setView(vueDialogue)
                .setPositiveButton("Enregistrer", null)
                .setNegativeButton("Annuler", null)
                .create();

        dialogue.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialogue.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String texteQuantite = etQuantite.getText() == null
                                        ? ""
                                        : etQuantite.getText().toString().trim();

                                Integer quantite = parseEntierPositif(texteQuantite);

                                if (quantite == null) {
                                    layoutQuantite.setError(
                                            "Entrez un nombre entier positif"
                                    );
                                    return;
                                }

                                layoutQuantite.setError(null);

                                stockManager.ajouter(
                                        typeFer,
                                        ConfigurationBarres.LONGUEUR_BARRE_STANDARD,
                                        quantite
                                );

                                rafraichirAffichageStock();
                                dialogue.dismiss();

                                Toast.makeText(
                                        StockActivity.this,
                                        "Stock mis à jour.",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
            }
        });

        dialogue.show();
    }

    // ------------------------------------------------------------------
    // Boîte de dialogue 2 : saisie de coupes
    // ------------------------------------------------------------------

    private void afficherDialogueSaisieCoupes(final String typeFer) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View vueDialogue = inflater.inflate(
                R.layout.dialog_saisie_stock,
                null
        );

        TextView tvType = vueDialogue.findViewById(R.id.tvTypeFerDialog);
        TextView tvUnite = vueDialogue.findViewById(R.id.tvUniteDialog);

        final TextInputLayout layoutQuantite =
                vueDialogue.findViewById(R.id.layoutQuantite);
        final TextInputEditText etQuantite =
                vueDialogue.findViewById(R.id.etQuantite);
        final TextInputLayout layoutLongueur =
                vueDialogue.findViewById(R.id.layoutLongueur);
        final TextInputEditText etLongueur =
                vueDialogue.findViewById(R.id.etLongueur);

        tvType.setText("Type : " + typeFer);
        tvUnite.setText("Unité : Coupes (longueur libre)");
        layoutQuantite.setHint("Nombre de coupes");
        layoutLongueur.setVisibility(View.VISIBLE);

        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setView(vueDialogue)
                .setPositiveButton("Enregistrer", null)
                .setNegativeButton("Annuler", null)
                .create();

        dialogue.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialogue.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String texteQuantite = etQuantite.getText() == null
                                        ? ""
                                        : etQuantite.getText().toString().trim();

                                String texteLongueur = etLongueur.getText() == null
                                        ? ""
                                        : etLongueur.getText().toString().trim();

                                Integer quantite = parseEntierPositif(texteQuantite);
                                Double longueur = parseReelPositif(texteLongueur);

                                boolean valide = true;

                                if (quantite == null) {
                                    layoutQuantite.setError(
                                            "Entrez un nombre entier positif"
                                    );
                                    valide = false;
                                } else {
                                    layoutQuantite.setError(null);
                                }

                                if (longueur == null) {
                                    layoutLongueur.setError(
                                            "Entrez une longueur valide"
                                    );
                                    valide = false;
                                } else {
                                    layoutLongueur.setError(null);
                                }

                                if (!valide) {
                                    return;
                                }

                                stockManager.ajouter(typeFer, longueur, quantite);
                                rafraichirAffichageStock();
                                dialogue.dismiss();

                                Toast.makeText(
                                        StockActivity.this,
                                        "Stock mis à jour.",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
            }
        });

        dialogue.show();
    }

    // ------------------------------------------------------------------
    // Utilitaires de validation de saisie
    // ------------------------------------------------------------------

    private Integer parseEntierPositif(String texte) {
        try {
            int valeur = Integer.parseInt(texte);
            return valeur > 0 ? valeur : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseReelPositif(String texte) {
        try {
            double valeur = Double.parseDouble(texte.replace(',', '.'));
            return valeur > 0 ? valeur : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}