package com.example.gkm_bar_fer;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.example.gkm_bar_fer.sauvegarde.SauvegardeAuto;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MenuActivity extends AppCompatActivity {

    private LinearLayout layoutSectionCalculsAccueil;
    private LinearLayout layoutCalculsAccueil;
    private TextInputEditText etRechercheCalculAccueil;
    private TextView tvCalculsVideAccueil;
    private String requeteRecherche = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        configurerMenuNavigation();
        appliquerDroitStock();

        layoutSectionCalculsAccueil = findViewById(R.id.layoutSectionCalculsAccueil);
        layoutCalculsAccueil = findViewById(R.id.layoutCalculsAccueil);
        etRechercheCalculAccueil = findViewById(R.id.etRechercheCalculAccueil);
        tvCalculsVideAccueil = findViewById(R.id.tvCalculsVideAccueil);

        if (etRechercheCalculAccueil != null) {
            etRechercheCalculAccueil.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    requeteRecherche = s == null ? "" : s.toString().trim();
                    rafraichirCalculsAccueil();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Android 7 à 9 : demande une seule fois l'autorisation d'écrire la sauvegarde.
        SauvegardeAuto.demanderPermissionSiNecessaire(this);

        Button btnPoteau = findViewById(R.id.btnMenuPoteau);
        Button btnSemelle = findViewById(R.id.btnMenuSemelle);
        Button btnDalle = findViewById(R.id.btnMenuDalle);
        Button btnPoutre = findViewById(R.id.btnMenuPoutre);
        Button btnPrePoteau = findViewById(R.id.btnMenuPrePoteau);
        Button btnChainage = findViewById(R.id.btnMenuChainage);
        Button btnMulti = findViewById(R.id.btnMenuMulti);
        Button btnStock = findViewById(R.id.btnMenuStock);

        btnPoteau.setOnClickListener(v -> ouvrirCalcul(TypeElement.POTEAU));
        btnSemelle.setOnClickListener(v -> ouvrirCalcul(TypeElement.SEMELLE));
        btnDalle.setOnClickListener(v -> ouvrirCalcul(TypeElement.DALLE));
        btnPoutre.setOnClickListener(v -> ouvrirCalcul(TypeElement.POUTRE));
        btnPrePoteau.setOnClickListener(v -> ouvrirCalcul(TypeElement.PRE_POTEAU));
        btnChainage.setOnClickListener(v -> ouvrirCalcul(TypeElement.CHAINAGE));
        btnMulti.setOnClickListener(v -> ouvrirCalculMulti());

        btnStock.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MenuActivity.this,
                    StockActivity.class
            );
            startActivity(intent);
        });
    }

    private void ouvrirCalcul(TypeElement type) {
        Intent intent = new Intent(
                MenuActivity.this,
                MainActivity.class
        );
        intent.putExtra(MainActivity.EXTRA_TYPE_ELEMENT, type.getCode());
        startActivity(intent);
    }

    private void ouvrirCalculMulti() {
        Intent intent = new Intent(
                MenuActivity.this,
                MainActivity.class
        );
        intent.putExtra(MainActivity.EXTRA_TYPE_ELEMENT, MainActivity.TYPE_MULTI);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // La licence a pu changer (nouveau code saisi depuis Paramètres).
        appliquerDroitStock();
        rafraichirCalculsAccueil();
    }

    private void rafraichirCalculsAccueil() {
        if (layoutCalculsAccueil == null || layoutSectionCalculsAccueil == null) {
            return;
        }
        layoutCalculsAccueil.removeAllViews();

        List<CalculEnregistre> tousLesCalculs =
                CalculManager.getInstance(this).getCalculsDuClient(CalculManager.CLIENT_ID_ACCUEIL);

        if (tousLesCalculs.isEmpty()) {
            layoutSectionCalculsAccueil.setVisibility(View.GONE);
            return;
        }
        layoutSectionCalculsAccueil.setVisibility(View.VISIBLE);

        List<CalculEnregistre> calculsFiltres = new ArrayList<>();
        for (CalculEnregistre c : tousLesCalculs) {
            if (requeteRecherche.isEmpty() || c.getNom().toLowerCase(Locale.FRANCE).contains(requeteRecherche.toLowerCase(Locale.FRANCE))) {
                calculsFiltres.add(c);
            }
        }

        if (calculsFiltres.isEmpty()) {
            if (tvCalculsVideAccueil != null) {
                tvCalculsVideAccueil.setText("Aucun calcul trouvé pour « " + requeteRecherche + " ».");
                tvCalculsVideAccueil.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (tvCalculsVideAccueil != null) {
            tvCalculsVideAccueil.setVisibility(View.GONE);
        }

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final CalculEnregistre calcul : calculsFiltres) {
            View itemView = inflater.inflate(R.layout.item_calcul, layoutCalculsAccueil, false);

            TextView tvNom = itemView.findViewById(R.id.tvNomCalcul);
            TextView tvDate = itemView.findViewById(R.id.tvDateCalcul);
            ImageButton btnSupprimer = itemView.findViewById(R.id.btnSupprimerCalcul);

            tvNom.setText(calcul.getNom());
            itemView.findViewById(R.id.viewAccentCalcul)
                    .setBackgroundColor(couleurType(calcul));
            boolean modifie = calcul.getDateModification() - calcul.getDateCreation() > 1000;
            String libelleType = calcul.isMulti()
                    ? getString(R.string.multi_calcul)
                    : calcul.getTypeElement().singulierMajuscule(MenuActivity.this);
            tvDate.setText(libelleType + " · "
                    + getString(modifie ? R.string.calcul_modifie_le : R.string.calcul_enregistre_le,
                    format.format(new Date(calcul.getDateModification()))));

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MenuActivity.this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_CALCUL_ID, calcul.getId());
                intent.putExtra(MainActivity.EXTRA_TYPE_ELEMENT, calcul.getTypeElement().getCode());
                startActivity(intent);
            });
            btnSupprimer.setOnClickListener(v -> afficherConfirmationSuppression(calcul));

            layoutCalculsAccueil.addView(itemView);
        }
    }

    private int couleurType(CalculEnregistre calcul) {
        if (calcul.isMulti()) {
            return Color.parseColor("#173B65");
        }
        switch (calcul.getTypeElement()) {
            case PRE_POTEAU:
                return Color.parseColor("#3E6B63");
            case DALLE:
                return Color.parseColor("#251603");
            case SEMELLE:
                return Color.parseColor("#54413A");
            case POUTRE:
                return Color.parseColor("#2E4A62");
            case CHAINAGE:
                return Color.parseColor("#7A2E3A");
            default:
                return Color.parseColor("#5B3E8E");
        }
    }

    private void afficherConfirmationSuppression(final CalculEnregistre calcul) {
        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setTitle(R.string.supprimer_calcul)
                .setMessage(getString(R.string.supprimer_calcul_message, calcul.getNom()))
                .setNegativeButton(R.string.dialog_ignorer, null)
                .setPositiveButton(R.string.supprimer, (d, which) -> {
                    boolean supprime = CalculManager.getInstance(MenuActivity.this)
                            .supprimer(calcul.getId());
                    Toast.makeText(MenuActivity.this,
                            supprime ? R.string.calcul_supprime : R.string.calcul_suppression_echec,
                            Toast.LENGTH_SHORT).show();
                    rafraichirCalculsAccueil();
                })
                .create();
        dialogue.show();
        dialogue.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.retirer_text));
    }

    /** Sans le droit « stock » : on cache l'accès au stock (menu latéral + bloc « Votre stock »). */
    private void appliquerDroitStock() {
        boolean autorise = AccesStock.autorise(this);
        AccesStock.appliquerAuTiroir((NavigationView) findViewById(R.id.navigationView), autorise);
        int visibilite = autorise ? View.VISIBLE : View.GONE;
        findViewById(R.id.tvTitreStock).setVisibility(visibilite);
        findViewById(R.id.cardStockAcces).setVisibility(visibilite);
    }

    private void configurerMenuNavigation() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        setSupportActionBar(toolbar);

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
        navigationView.setCheckedItem(R.id.navAccueil);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navAccueil) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            if (id == R.id.navStockDechets) {
                drawerLayout.closeDrawer(GravityCompat.START);

                Intent intent = new Intent(
                        MenuActivity.this,
                        StockActivity.class
                );
                startActivity(intent);
                return true;
            }

            if (id == R.id.navStockClient) {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MenuActivity.this, StockClientsActivity.class));
                return true;
            }

            if (id == R.id.navEspaceClient) {
                drawerLayout.closeDrawer(GravityCompat.START);

                Intent intent = new Intent(
                        MenuActivity.this,
                        EspaceClientActivity.class
                );
                startActivity(intent);
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
}
