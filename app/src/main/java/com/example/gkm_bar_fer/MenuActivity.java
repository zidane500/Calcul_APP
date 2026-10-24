package com.example.gkm_bar_fer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        configurerMenuNavigation();

        Button btnPoteau = findViewById(R.id.btnMenuPoteau);
        Button btnSemelle = findViewById(R.id.btnMenuSemelle);
        Button btnDalle = findViewById(R.id.btnMenuDalle);
        Button btnPoutre = findViewById(R.id.btnMenuPoutre);
        Button btnPrePoteau = findViewById(R.id.btnMenuPrePoteau);
        Button btnMulti = findViewById(R.id.btnMenuMulti);
        Button btnStock = findViewById(R.id.btnMenuStock);

        btnPoteau.setOnClickListener(v -> ouvrirCalcul(TypeElement.POTEAU));
        btnSemelle.setOnClickListener(v -> ouvrirCalcul(TypeElement.SEMELLE));
        btnDalle.setOnClickListener(v -> ouvrirCalcul(TypeElement.DALLE));
        btnPoutre.setOnClickListener(v -> ouvrirCalcul(TypeElement.POUTRE));
        btnPrePoteau.setOnClickListener(v -> ouvrirCalcul(TypeElement.PRE_POTEAU));
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