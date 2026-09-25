package com.example.gkm_bar_fer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        Button btnPoteau = findViewById(R.id.btnMenuPoteau);
        Button btnSemelle = findViewById(R.id.btnMenuSemelle);

        // Poteau et Semelle ouvrent la même interface de calcul pour l'instant
        View.OnClickListener ouvrirCalcul = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, MainActivity.class));
            }
        };

        btnPoteau.setOnClickListener(ouvrirCalcul);
        btnSemelle.setOnClickListener(ouvrirCalcul);

        // Escalier et Dalle : pas encore fonctionnels, aucune action pour l'instant
        // (en attente de vos instructions pour la suite)
    }
}