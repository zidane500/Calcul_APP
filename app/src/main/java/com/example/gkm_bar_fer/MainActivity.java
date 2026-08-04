package com.example.gkm_bar_fer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText etNombrePoteaux, etLongueurPoteaux;
    private AutoCompleteTextView autoCompleteBarreType;
    private LinearLayout layoutBarresSelectionnees;
    private TextView tvAucuneBarre;
    private Button btnCalculer;
    private CardView cardResultats;
    private TextView tvResultats;

    private static final double LONGUEUR_BARRE_STANDARD = 12.0;
    private List<String> typesBarresDisponibles;
    private Map<String, TextInputEditText> barresSelectionneesMap;
    private ArrayAdapter<String> adapterTypesBarres;

    // Classe pour stocker les résultats de calcul
    private static class ResultatCalcul {
        int barresNecessaires;
        List<Double> listeDechets;  // Liste des déchets individuels
        boolean utilisationOptimisee;

        ResultatCalcul(int barresNecessaires, List<Double> listeDechets, boolean utilisationOptimisee) {
            this.barresNecessaires = barresNecessaires;
            this.listeDechets = listeDechets;
            this.utilisationOptimisee = utilisationOptimisee;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initTypesBarres();
        initViews();
        setupListeners();
    }

    private void initTypesBarres() {
        typesBarresDisponibles = new ArrayList<>();
        // Ajoutez tous les types de barres de N6 à N30
        for (int i = 6; i <= 30; i += 2) {
            typesBarresDisponibles.add("N" + i);
        }
        // Ajoutez aussi les types supplémentaires si nécessaire
        typesBarresDisponibles.add("N32");
        typesBarresDisponibles.add("N35");
        typesBarresDisponibles.add("N40");

        barresSelectionneesMap = new HashMap<>();
    }

    private void initViews() {
        etNombrePoteaux = findViewById(R.id.etNombrePoteaux);
        etLongueurPoteaux = findViewById(R.id.etLongueurPoteaux);
        autoCompleteBarreType = findViewById(R.id.autoCompleteBarreType);
        layoutBarresSelectionnees = findViewById(R.id.layoutBarresSelectionnees);
        tvAucuneBarre = findViewById(R.id.tvAucuneBarre);
        btnCalculer = findViewById(R.id.btnCalculer);
        cardResultats = findViewById(R.id.cardResultats);
        tvResultats = findViewById(R.id.tvResultats);

        // Configurer l'adapteur pour le dropdown
        adapterTypesBarres = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, typesBarresDisponibles);
        autoCompleteBarreType.setAdapter(adapterTypesBarres);
    }

    private void setupListeners() {
        // Listener pour la sélection du type de barre
        autoCompleteBarreType.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String typeSelectionne = (String) parent.getItemAtPosition(position);
                ajouterBarreSelectionnee(typeSelectionne);
                autoCompleteBarreType.setText("");
            }
        });

        btnCalculer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculerBarres();
            }
        });
    }

    private void ajouterBarreSelectionnee(String typeBarre) {
        // Vérifier si le type n'est pas déjà sélectionné
        if (barresSelectionneesMap.containsKey(typeBarre)) {
            Toast.makeText(this, typeBarre + " est déjà sélectionné", Toast.LENGTH_SHORT).show();
            return;
        }

        // Créer le layout pour la nouvelle barre
        LayoutInflater inflater = LayoutInflater.from(this);
        View barreView = inflater.inflate(R.layout.item_barre_selectionnee, layoutBarresSelectionnees, false);

        // Configurer les vues
        TextView tvTypeBarre = barreView.findViewById(R.id.tvTypeBarre);
        TextInputEditText etNombreBarres = barreView.findViewById(R.id.etNombreBarres);
        ImageButton btnSupprimer = barreView.findViewById(R.id.btnSupprimer);

        tvTypeBarre.setText(typeBarre);
        etNombreBarres.setText("0");

        // Stocker la référence à l'EditText
        barresSelectionneesMap.put(typeBarre, etNombreBarres);

        // Listener pour supprimer la barre
        btnSupprimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                supprimerBarreSelectionnee(typeBarre, barreView);
            }
        });

        // Ajouter la vue au layout
        layoutBarresSelectionnees.addView(barreView);

        // Masquer le message "Aucune barre"
        tvAucuneBarre.setVisibility(View.GONE);
    }

    private void supprimerBarreSelectionnee(String typeBarre, View barreView) {
        barresSelectionneesMap.remove(typeBarre);
        layoutBarresSelectionnees.removeView(barreView);

        // Afficher le message "Aucune barre" si plus aucune barre n'est sélectionnée
        if (barresSelectionneesMap.isEmpty()) {
            tvAucuneBarre.setVisibility(View.VISIBLE);
        }
    }

    private void calculerBarres() {
        if (!validerEntrees()) {
            return;
        }

        int nombrePoteaux = Integer.parseInt(etNombrePoteaux.getText().toString());
        double longueurPoteaux = Double.parseDouble(etLongueurPoteaux.getText().toString());

        // Récupérer les barres sélectionnées avec leurs quantités
        Map<String, Integer> barresParType = new HashMap<>();
        for (Map.Entry<String, TextInputEditText> entry : barresSelectionneesMap.entrySet()) {
            String typeBarre = entry.getKey();
            int nombre = getValeur(entry.getValue());
            if (nombre > 0) {
                barresParType.put(typeBarre, nombre);
            }
        }

        // Vérifier qu'au moins une barre a été saisie
        if (barresParType.isEmpty()) {
            tvResultats.setText("Veuillez saisir au moins un type de barre avec une quantité positive");
            cardResultats.setVisibility(View.VISIBLE);
            return;
        }

        // Calcul des résultats
        StringBuilder resultats = new StringBuilder();
        resultats.append("Résultats pour ").append(nombrePoteaux).append(" poteaux de ")
                .append(longueurPoteaux).append(" mètres:\n\n");

        for (Map.Entry<String, Integer> entry : barresParType.entrySet()) {
            String typeBarre = entry.getKey();
            int nombreParPoteau = entry.getValue();

            if (nombreParPoteau > 0) {
                int totalCoupesNecessaires = nombreParPoteau * nombrePoteaux;
                ResultatCalcul resultat = calculerBarresNecessaires(totalCoupesNecessaires, longueurPoteaux);

                resultats.append("• ").append(typeBarre).append(":\n");
                resultats.append("  - Coupes/poteau: ").append(nombreParPoteau).append("\n");
                resultats.append("  - Total nécessaire: ").append(totalCoupesNecessaires).append(" coupes\n");
                resultats.append("  - Barres à acheter: ").append(resultat.barresNecessaires).append("\n");

                // AFFICHAGE DES DÉCHETS GROUPÉS PAR LONGUEUR
                if (resultat.listeDechets.isEmpty()) {
                    resultats.append("  - Déchets: Aucun\n");
                } else {
                    resultats.append("  - Déchets: ").append(resultat.listeDechets.size()).append(" chutes\n");

                    // Compter les chutes par longueur
                    Map<Double, Integer> dechetsParLongueur = new HashMap<>();
                    for (Double dechet : resultat.listeDechets) {
                        dechetsParLongueur.put(dechet, dechetsParLongueur.getOrDefault(dechet, 0) + 1);
                    }

                    // Afficher les chutes groupées par longueur
                    for (Map.Entry<Double, Integer> entryDechet : dechetsParLongueur.entrySet()) {
                        if (entryDechet.getValue() == 1) {
                            resultats.append("    * Chute: ")
                                    .append(String.format(Locale.FRANCE, "%.2f", entryDechet.getKey())).append(" m\n");
                        } else {
                            resultats.append("    * ").append(entryDechet.getValue()).append(" chutes: ")
                                    .append(String.format(Locale.FRANCE, "%.2f", entryDechet.getKey())).append(" m\n");
                        }
                    }
                }

                resultats.append("  - Optimisé: ").append(resultat.utilisationOptimisee ? "Oui" : "Non").append("\n\n");
            }
        }

        tvResultats.setText(resultats.toString());
        cardResultats.setVisibility(View.VISIBLE);
    }

    private ResultatCalcul calculerBarresNecessaires(int totalCoupesNecessaires, double longueurPoteau) {
        if (longueurPoteau <= 0 || totalCoupesNecessaires <= 0) {
            return new ResultatCalcul(0, new ArrayList<>(), false);
        }

        // Si la coupe est plus longue qu'une barre standard
        if (longueurPoteau > LONGUEUR_BARRE_STANDARD) {
            int barresNecessaires = totalCoupesNecessaires;
            List<Double> dechets = new ArrayList<>();
            for (int i = 0; i < totalCoupesNecessaires; i++) {
                dechets.add(LONGUEUR_BARRE_STANDARD - longueurPoteau);
            }
            return new ResultatCalcul(barresNecessaires, dechets, false);
        }

        // CALCUL INTELLIGENT AVEC RÉUTILISATION DES CHUTES
        int barresNecessaires = 0;
        int coupesRestantes = totalCoupesNecessaires;
        List<Double> chutesUtilisables = new ArrayList<>(); // Stocke les chutes réutilisables
        List<Double> listeDechets = new ArrayList<>(); // Liste des déchets individuels

        while (coupesRestantes > 0) {
            double longueurDisponible = LONGUEUR_BARRE_STANDARD;
            barresNecessaires++;

            // ESSAYER D'ABORD D'UTILISER LES CHUTES EXISTANTES
            Iterator<Double> iterator = chutesUtilisables.iterator();
            while (iterator.hasNext()) {
                double chute = iterator.next();
                if (chute >= longueurPoteau && coupesRestantes > 0) {
                    // On peut utiliser cette chute pour faire une coupe !
                    coupesRestantes--;
                    longueurDisponible = chute - longueurPoteau;
                    iterator.remove(); // Cette chute est utilisée

                    // Ce qui reste de la chute peut-il être réutilisé ?
                    if (longueurDisponible >= longueurPoteau) {
                        chutesUtilisables.add(longueurDisponible);
                        longueurDisponible = 0;
                    } else if (longueurDisponible > 0) {
                        // Trop petit → déchet
                        listeDechets.add(longueurDisponible);
                    }
                    break;
                }
            }

            // MAINTENANT UTILISER LA BARRE STANDARD (12m)
            while (longueurDisponible >= longueurPoteau && coupesRestantes > 0) {
                coupesRestantes--;
                longueurDisponible -= longueurPoteau;
            }

            // GÉRER CE QUI RESTE DE LA BARRE
            if (longueurDisponible > 0) {
                if (longueurDisponible >= longueurPoteau) {
                    // Peut être réutilisé pour un autre poteau
                    chutesUtilisables.add(longueurDisponible);
                } else {
                    // Trop petit → déchet
                    listeDechets.add(longueurDisponible);
                }
            }
        }

        // AJOUTER LES CHUTES NON UTILISÉES COMME DÉCHETS
        listeDechets.addAll(chutesUtilisables);

        // Calculer si l'utilisation est optimisée
        double totalDechets = 0;
        for (double dechet : listeDechets) {
            totalDechets += dechet;
        }
        boolean utilisationOptimisee = (totalDechets / (barresNecessaires * LONGUEUR_BARRE_STANDARD)) < 0.2;

        return new ResultatCalcul(barresNecessaires, listeDechets, utilisationOptimisee);
    }

    private boolean validerEntrees() {
        if (TextUtils.isEmpty(etNombrePoteaux.getText()) ||
                TextUtils.isEmpty(etLongueurPoteaux.getText())) {
            tvResultats.setText("Veuillez remplir le nombre de poteaux et la longueur");
            cardResultats.setVisibility(View.VISIBLE);
            return false;
        }

        try {
            int nombrePoteaux = Integer.parseInt(etNombrePoteaux.getText().toString());
            double longueur = Double.parseDouble(etLongueurPoteaux.getText().toString());

            if (nombrePoteaux <= 0 || longueur <= 0) {
                tvResultats.setText("Les valeurs doivent être positives");
                cardResultats.setVisibility(View.VISIBLE);
                return false;
            }
        } catch (NumberFormatException e) {
            tvResultats.setText("Veuillez entrer des nombres valides");
            cardResultats.setVisibility(View.VISIBLE);
            return false;
        }

        return true;
    }

    private int getValeur(TextInputEditText editText) {
        if (TextUtils.isEmpty(editText.getText())) {
            return 0;
        }
        try {
            return Integer.parseInt(editText.getText().toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}