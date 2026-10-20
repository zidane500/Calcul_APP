package com.example.gkm_bar_fer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Page d'un client : boutons de calcul (poteau, dalle, semelle, poutre, pré-poteau)
 * + liste de ses calculs enregistrés.
 */
public class CalculsClientActivity extends AppCompatActivity {

    public static final String EXTRA_CLIENT_ID = "client_id";

    private String clientId;
    private Client client;
    private LinearLayout layoutCalculs;
    private TextView tvAucunCalcul;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculs_client);

        clientId = getIntent().getStringExtra(EXTRA_CLIENT_ID);
        client = clientId == null
                ? null
                : ClientManager.getInstance(this).trouverParId(clientId);

        if (client == null) {
            Toast.makeText(this, "Client introuvable.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        configurerHeader();

        layoutCalculs = findViewById(R.id.layoutCalculs);
        tvAucunCalcul = findViewById(R.id.tvAucunCalcul);
        TextView tvInfoClient = findViewById(R.id.tvInfoClient);

        if (client.getVille().isEmpty()) {
            tvInfoClient.setVisibility(View.GONE);
        } else {
            tvInfoClient.setVisibility(View.VISIBLE);
            tvInfoClient.setText(client.getVille());
        }

        findViewById(R.id.btnCalculerPoteau)
                .setOnClickListener(v -> ouvrirNouveauCalcul(TypeElement.POTEAU));
        findViewById(R.id.btnCalculerDalle)
                .setOnClickListener(v -> ouvrirNouveauCalcul(TypeElement.DALLE));
        findViewById(R.id.btnCalculerSemelle)
                .setOnClickListener(v -> ouvrirNouveauCalcul(TypeElement.SEMELLE));
        findViewById(R.id.btnCalculerPoutre)
                .setOnClickListener(v -> ouvrirNouveauCalcul(TypeElement.POUTRE));
        findViewById(R.id.btnCalculerPrePoteau)
                .setOnClickListener(v -> ouvrirNouveauCalcul(TypeElement.PRE_POTEAU));


        rafraichirAffichage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (client != null) {
            rafraichirAffichage();
        }
    }

    private void ouvrirNouveauCalcul(TypeElement type) {
        Intent intent = new Intent(CalculsClientActivity.this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_CLIENT_ID, clientId);
        intent.putExtra(MainActivity.EXTRA_TYPE_ELEMENT, type.getCode());
        startActivity(intent);
    }

    private void configurerHeader() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(client.getNom());
        }

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void rafraichirAffichage() {
        layoutCalculs.removeAllViews();

        List<CalculEnregistre> calculs =
                CalculManager.getInstance(this).getCalculsDuClient(clientId);

        if (calculs.isEmpty()) {
            tvAucunCalcul.setVisibility(View.VISIBLE);
            return;
        }
        tvAucunCalcul.setVisibility(View.GONE);

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final CalculEnregistre calcul : calculs) {
            View itemView = inflater.inflate(R.layout.item_calcul, layoutCalculs, false);

            TextView tvNom = itemView.findViewById(R.id.tvNomCalcul);
            TextView tvDate = itemView.findViewById(R.id.tvDateCalcul);
            Button btnOuvrir = itemView.findViewById(R.id.btnOuvrirCalcul);

            tvNom.setText(calcul.getNom());
            boolean modifie = calcul.getDateModification() - calcul.getDateCreation() > 1000;
            tvDate.setText(calcul.getTypeElement().getSingulierMajuscule() + " · "
                    + (modifie ? "Modifié le " : "Enregistré le ")
                    + format.format(new Date(calcul.getDateModification())));

            btnOuvrir.setOnClickListener(v -> {
                Intent intent = new Intent(CalculsClientActivity.this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_CLIENT_ID, clientId);
                intent.putExtra(MainActivity.EXTRA_CALCUL_ID, calcul.getId());
                intent.putExtra(MainActivity.EXTRA_TYPE_ELEMENT, calcul.getTypeElement().getCode());
                startActivity(intent);
            });

            layoutCalculs.addView(itemView);
        }
    }
}