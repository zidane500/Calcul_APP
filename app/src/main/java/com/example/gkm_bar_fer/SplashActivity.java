package com.example.gkm_bar_fer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long DUREE_ANIMATION_MS = 600;
    private static final long DELAI_TEXTE_MS = 200;
    private static final long DUREE_AFFICHAGE_TOTAL_MS = 1600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView ivLogo = findViewById(R.id.ivLogoSplash);
        TextView tvNom = findViewById(R.id.tvNomAppSplash);

        // Petite animation d'apparition professionnelle : léger zoom sur le logo (déjà visible),
        // puis fondu du nom de l'appli
        ivLogo.setScaleX(0.8f);
        ivLogo.setScaleY(0.8f);
        ivLogo.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(DUREE_ANIMATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        tvNom.animate()
                .alpha(1f)
                .setStartDelay(DELAI_TEXTE_MS)
                .setDuration(DUREE_ANIMATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                demarrerActivitePrincipale();
            }
        }, DUREE_AFFICHAGE_TOTAL_MS);
    }

    private void demarrerActivitePrincipale() {
        startActivity(new Intent(SplashActivity.this, MenuActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}