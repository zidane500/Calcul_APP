package com.example.gkm_bar_fer;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

        Button btnRetirerFer = findViewById(R.id.btnRetirerFer);
        btnRetirerFer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                afficherDialogueRetirerFer();
            }
        });

        Button btnHistoriqueStock = findViewById(R.id.btnHistoriqueStock);
        btnHistoriqueStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                afficherDialogueHistorique();
            }
        });

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
    // Boîte de dialogue : historique des mouvements du stock
    // ------------------------------------------------------------------

    private void afficherDialogueHistorique() {
        View vue = LayoutInflater.from(this).inflate(R.layout.dialog_historique_stock, null);
        LinearLayout conteneur = vue.findViewById(R.id.layoutHistorique);
        TextView tvVide = vue.findViewById(R.id.txtHistoriqueVide);

        List<MouvementStock> mouvements = stockManager.getHistorique();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);
        float densite = getResources().getDisplayMetrics().density;

        tvVide.setVisibility(mouvements.isEmpty() ? View.VISIBLE : View.GONE);

        // Regroupe les mouvements consécutifs d'une même opération (ex : coupe = retrait + reste)
        List<List<MouvementStock>> groupes = new ArrayList<>();
        String groupeCourant = null;
        for (MouvementStock m : mouvements) {
            boolean memeGroupe = !groupes.isEmpty()
                    && !m.groupe.isEmpty()
                    && m.groupe.equals(groupeCourant);
            if (!memeGroupe) {
                groupes.add(new ArrayList<MouvementStock>());
            }
            groupes.get(groupes.size() - 1).add(0, m);   // ordre chronologique dans le bloc
            groupeCourant = m.groupe;
        }

        for (List<MouvementStock> groupe : groupes) {
            MouvementStock premier = groupe.get(0);

            boolean aAjout = false;
            boolean aRetrait = false;
            for (MouvementStock m : groupe) {
                if (MouvementStock.AJOUT.equals(m.action)) {
                    aAjout = true;
                } else {
                    aRetrait = true;
                }
            }

            View carte = LayoutInflater.from(this)
                    .inflate(R.layout.item_historique_stock, conteneur, false);
            TextView tvBadge = carte.findViewById(R.id.tvCarteBadge);
            TextView tvDate = carte.findViewById(R.id.tvCarteDate);
            TextView tvTitre = carte.findViewById(R.id.tvCarteTitre);
            LinearLayout layoutLignes = carte.findViewById(R.id.layoutLignes);

            String texteBadge;
            int couleurTexte;
            int couleurFond;
            if (aAjout && aRetrait) {
                texteBadge = "OPÉRATION";
                couleurTexte = 0xFF1565C0;
                couleurFond = 0xFFE3F2FD;
            } else if (aAjout) {
                texteBadge = "AJOUT";
                couleurTexte = 0xFF2E7D32;
                couleurFond = 0xFFE8F5E9;
            } else {
                texteBadge = "RETRAIT";
                couleurTexte = 0xFFC62828;
                couleurFond = 0xFFFDECEA;
            }

            GradientDrawable fondBadge = new GradientDrawable();
            fondBadge.setColor(couleurFond);
            fondBadge.setCornerRadius(10 * densite);
            tvBadge.setBackground(fondBadge);
            tvBadge.setText(texteBadge);
            tvBadge.setTextColor(couleurTexte);

            tvDate.setText(format.format(new Date(premier.date)));

            String titre = premier.typeFer;
            if (!premier.motif.isEmpty()) {
                titre += " · " + premier.motif;
            }
            tvTitre.setText(titre);

            for (MouvementStock m : groupe) {
                View ligne = LayoutInflater.from(this)
                        .inflate(R.layout.item_historique_ligne, layoutLignes, false);
                TextView tvLibelle = ligne.findViewById(R.id.tvLigneLibelle);
                TextView tvValeur = ligne.findViewById(R.id.tvLigneValeur);

                boolean ajout = MouvementStock.AJOUT.equals(m.action);
                String libelle = ajout ? "Ajouté" : "Retiré";
                if (!m.motif.isEmpty() && !m.motif.equals(premier.motif)) {
                    libelle = m.motif;   // ex : « Reste remis en stock »
                }

                tvLibelle.setText((ajout ? "+ " : "− ") + libelle);
                tvValeur.setText(String.format(Locale.FRANCE, "%.2f m × %d", m.longueur, m.quantite));

                int couleur = ContextCompat.getColor(this, ajout ? R.color.success : R.color.error);
                tvLibelle.setTextColor(couleur);
                tvValeur.setTextColor(couleur);

                layoutLignes.addView(ligne);
            }

            conteneur.addView(carte);
        }

        new AlertDialog.Builder(this)
                .setTitle("Historique du stock")
                .setView(vue)
                .setPositiveButton("Fermer", null)
                .show();
    }

    // ------------------------------------------------------------------
    // Boîte de dialogue : retirer du fer du stock (coupe)
    // ------------------------------------------------------------------

    private void afficherDialogueRetirerFer() {
        final List<String> types = new ArrayList<>();
        for (Map.Entry<String, List<MorceauFer>> entree : stockManager.getStock().entrySet()) {
            if (entree.getValue() != null && !entree.getValue().isEmpty()) {
                types.add(entree.getKey());
            }
        }

        if (types.isEmpty()) {
            Toast.makeText(this, "Le stock est vide : rien à retirer.", Toast.LENGTH_SHORT).show();
            return;
        }

        View vue = LayoutInflater.from(this).inflate(R.layout.dialog_retirer_fer, null);
        final Spinner spType = vue.findViewById(R.id.spinnerTypeRetrait);
        final Spinner spLongueur = vue.findViewById(R.id.spinnerLongueurRetrait);
        final TextView tvLabelRequise = vue.findViewById(R.id.txtLabelRequise);
        final EditText etRequise = vue.findViewById(R.id.editLongueurRequise);
        final TextView tvReste = vue.findViewById(R.id.txtResteRetrait);
        final TextView tvDisponible = vue.findViewById(R.id.txtDisponible);
        final EditText etNombre = vue.findViewById(R.id.editNombreRetrait);
        final TextView tvErreur = vue.findViewById(R.id.txtErreurRetrait);

        spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));

        final List<MorceauFer> morceauxAffiches = new ArrayList<>();
        final ArrayAdapter<String> adapterLongueur = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<String>());
        spLongueur.setAdapter(adapterLongueur);
        final int[] positionLongueur = {0};

        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setTitle("Retirer Fer")
                .setView(vue)
                .setPositiveButton("Confirmer", null)
                .setNegativeButton("Ignorer", null)
                .create();

        final Runnable valider = () -> {
            int position = positionLongueur[0];
            boolean selectionOk = position >= 0 && position < morceauxAffiches.size();
            int disponible = selectionOk ? morceauxAffiches.get(position).quantite : 0;
            double longueurMorceau = selectionOk ? morceauxAffiches.get(position).longueur : 0.0;

            tvDisponible.setText("Disponible : " + disponible + " morceau(x)");
            tvLabelRequise.setText(selectionOk
                    ? "Longueur requise (max "
                      + String.format(Locale.FRANCE, "%.2f", longueurMorceau) + " m)"
                    : "Longueur requise");

            String texteRequise = etRequise.getText() == null
                    ? "" : etRequise.getText().toString().trim();
            String texteNombre = etNombre.getText() == null
                    ? "" : etNombre.getText().toString().trim();

            String erreur = null;
            boolean requiseOk = false;
            boolean nombreOk = false;
            double requise = 0.0;
            int nombre = 0;

            if (!texteRequise.isEmpty()) {
                Double lue = parseReelPositif(texteRequise);
                if (lue == null || arrondirCm(lue) <= 0.0) {
                    erreur = "Entrez une longueur requise valide";
                } else {
                    requise = arrondirCm(lue);
                    if (requise > longueurMorceau + 1e-9) {
                        erreur = "La longueur requise ne peut pas dépasser "
                                + String.format(Locale.FRANCE, "%.2f", longueurMorceau) + " m";
                    } else {
                        requiseOk = true;
                    }
                }
            }

            if (erreur == null && !texteNombre.isEmpty()) {
                Integer lu = parseEntierPositif(texteNombre);
                if (lu == null) {
                    erreur = "Entrez un nombre entier positif";
                } else if (lu > disponible) {
                    erreur = "Maximum disponible : " + disponible;
                } else {
                    nombre = lu;
                    nombreOk = true;
                }
            }

            // Aperçu du reste remis en stock
            if (requiseOk) {
                double reste = arrondirCm(longueurMorceau - requise);
                if (reste > 1e-9) {
                    tvReste.setText("Reste remis en stock : "
                            + String.format(Locale.FRANCE, "%.2f", reste) + " m"
                            + (nombreOk && nombre > 1 ? " par morceau" : ""));
                } else {
                    tvReste.setText("Aucun reste : le morceau est utilisé en entier");
                }
                tvReste.setVisibility(View.VISIBLE);
            } else {
                tvReste.setVisibility(View.GONE);
            }

            tvErreur.setText(erreur);
            tvErreur.setVisibility(erreur == null ? View.GONE : View.VISIBLE);

            Button btnConfirmer = dialogue.getButton(AlertDialog.BUTTON_POSITIVE);
            if (btnConfirmer != null) {
                btnConfirmer.setEnabled(requiseOk && nombreOk);
            }
        };

        final Runnable chargerLongueurs = () -> {
            int positionType = spType.getSelectedItemPosition();
            morceauxAffiches.clear();
            adapterLongueur.clear();

            if (positionType >= 0 && positionType < types.size()) {
                for (MorceauFer m : stockManager.obtenirMorceaux(types.get(positionType))) {
                    if (m.quantite > 0) {
                        morceauxAffiches.add(m);
                        adapterLongueur.add(String.format(Locale.FRANCE, "%.2f m", m.longueur));
                    }
                }
            }

            adapterLongueur.notifyDataSetChanged();
            spLongueur.setSelection(0);
            positionLongueur[0] = 0;
            etRequise.setText("");
            etNombre.setText("");
            valider.run();
        };

        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                chargerLongueurs.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spLongueur.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                positionLongueur[0] = position;
                etRequise.setText("");
                etNombre.setText("");
                valider.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        TextWatcher surveillant = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                valider.run();
            }
        };
        etRequise.addTextChangedListener(surveillant);
        etNombre.addTextChangedListener(surveillant);

        dialogue.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button btnConfirmer = dialogue.getButton(AlertDialog.BUTTON_POSITIVE);
                btnConfirmer.setEnabled(false);

                btnConfirmer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int positionType = spType.getSelectedItemPosition();
                        int position = positionLongueur[0];

                        if (positionType < 0 || positionType >= types.size()
                                || position < 0 || position >= morceauxAffiches.size()) {
                            return;
                        }

                        String typeFer = types.get(positionType);
                        double longueur = morceauxAffiches.get(position).longueur;

                        Double lue = parseReelPositif(etRequise.getText() == null
                                ? "" : etRequise.getText().toString().trim());
                        Integer nombre = parseEntierPositif(etNombre.getText() == null
                                ? "" : etNombre.getText().toString().trim());
                        if (lue == null || nombre == null) {
                            return;
                        }

                        double requise = arrondirCm(lue);
                        if (requise <= 0.0 || requise > longueur + 1e-9) {
                            tvErreur.setText("La longueur requise ne peut pas dépasser "
                                    + String.format(Locale.FRANCE, "%.2f", longueur) + " m");
                            tvErreur.setVisibility(View.VISIBLE);
                            return;
                        }

                        int disponible = quantiteEnStock(typeFer, longueur);
                        if (nombre > disponible) {
                            tvErreur.setText("Maximum disponible : " + disponible);
                            tvErreur.setVisibility(View.VISIBLE);
                            return;
                        }

                        double reste = arrondirCm(longueur - requise);

                        // Retire les morceaux utilisés et remet les restes en stock,
                        // le tout dans un seul bloc de l'historique
                        if (!stockManager.couper(typeFer, longueur, requise, nombre)) {
                            tvErreur.setText("Opération impossible : vérifiez le stock disponible");
                            tvErreur.setVisibility(View.VISIBLE);
                            return;
                        }

                        rafraichirAffichageStock();
                        dialogue.dismiss();

                        String message = nombre + " coupe(s) de "
                                + String.format(Locale.FRANCE, "%.2f", requise)
                                + " m dans " + String.format(Locale.FRANCE, "%.2f", longueur)
                                + " m (" + typeFer + ").";
                        if (reste > 1e-9) {
                            message += " Reste remis en stock : "
                                    + String.format(Locale.FRANCE, "%.2f", reste) + " m.";
                        }
                        Toast.makeText(StockActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        dialogue.show();
        chargerLongueurs.run();
    }

    /** Arrondit une longueur au centimètre pour éviter 3,6999999 m. */
    private double arrondirCm(double valeur) {
        return Math.round(valeur * 100.0) / 100.0;
    }

    private int quantiteEnStock(String typeFer, double longueur) {
        for (MorceauFer m : stockManager.obtenirMorceaux(typeFer)) {
            if (Math.abs(m.longueur - longueur) < 1e-6) {
                return m.quantite;
            }
        }
        return 0;
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
