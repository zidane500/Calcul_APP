package com.example.gkm_bar_fer;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

import com.example.gkm_bar_fer.licence.LicenseManager;
import com.google.android.material.navigation.NavigationView;

/**
 * Droit « utiliser les fonctionnalités du stock » (défini dans la licence).
 *
 * Quand le droit est accordé : l'application se comporte comme avant.
 * Quand il ne l'est pas : tout ce qui concerne le stock est caché (menu latéral, accueil,
 * options de calcul, boutons des résultats). La gestion des clients et les calculs
 * fonctionnent normalement. Les données de stock déjà enregistrées ne sont jamais effacées.
 *
 * La protection est double : cette classe cache les éléments, et LicenseGuard ferme les
 * écrans de stock si quelqu'un parvenait à les ouvrir quand même.
 */
public final class AccesStock {

    private AccesStock() {
    }

    /** @return true si la licence est valide ET autorise les fonctionnalités du stock. */
    public static boolean autorise(Context contexte) {
        return LicenseManager.getInstance(contexte).peutUtiliserStock();
    }

    /** Affiche ou cache les deux entrées « stock » du menu latéral. */
    public static void appliquerAuTiroir(NavigationView navigationView, boolean autorise) {
        if (navigationView == null) {
            return;
        }
        Menu menu = navigationView.getMenu();
        afficher(menu.findItem(R.id.navStockDechets), autorise);
        afficher(menu.findItem(R.id.navStockClient), autorise);
    }

    private static void afficher(MenuItem item, boolean visible) {
        if (item != null) {
            item.setVisible(visible);
        }
    }
}
