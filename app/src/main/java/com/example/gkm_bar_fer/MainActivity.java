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

    private LinearLayout layoutGroupesPoteaux;
    private Button btnAjouterPoteau;
    private Button btnCalculer;
    private CardView cardResultats;
    private TextView tvResultats;

    private static final double LONGUEUR_BARRE_STANDARD = 12.0;
    private List<String> typesBarresDisponibles;
    private List<GroupePoteau> listeGroupesPoteaux;

    // Représente un "type de poteau" : ses infos (nombre/longueur) + les barres qui lui sont associées.
    private static class GroupePoteau {
        View rootView;
        TextInputEditText etNombrePoteaux;
        TextInputEditText etLongueurPoteaux;
        AutoCompleteTextView autoCompleteBarreType;
        LinearLayout layoutBarresSelectionnees;
        TextView tvAucuneBarre;
        Map<String, TextInputEditText> barresSelectionneesMap = new HashMap<>();
    }

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

        // Premier groupe de poteau affiché par défaut, sans bouton "Supprimer"
        ajouterGroupePoteau(false);
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

        listeGroupesPoteaux = new ArrayList<>();
    }

    private void initViews() {
        layoutGroupesPoteaux = findViewById(R.id.layoutGroupesPoteaux);
        btnAjouterPoteau = findViewById(R.id.btnAjouterPoteau);
        btnCalculer = findViewById(R.id.btnCalculer);
        cardResultats = findViewById(R.id.cardResultats);
        tvResultats = findViewById(R.id.tvResultats);
    }

    private void setupListeners() {
        btnAjouterPoteau.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ajouterGroupePoteau(true);
            }
        });

        btnCalculer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculerBarres();
            }
        });
    }

    /**
     * Ajoute un nouveau bloc "poteau" (infos + sélection des barres) dans le conteneur.
     * @param peutEtreSupprime false pour le tout premier bloc (pas de bouton Supprimer),
     *                          true pour les blocs ajoutés via "Ajouter un autre poteau".
     */
    private void ajouterGroupePoteau(boolean peutEtreSupprime) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View groupeView = inflater.inflate(R.layout.item_groupe_poteau, layoutGroupesPoteaux, false);

        final GroupePoteau groupe = new GroupePoteau();
        groupe.rootView = groupeView;
        groupe.etNombrePoteaux = groupeView.findViewById(R.id.etNombrePoteaux);
        groupe.etLongueurPoteaux = groupeView.findViewById(R.id.etLongueurPoteaux);
        groupe.autoCompleteBarreType = groupeView.findViewById(R.id.autoCompleteBarreType);
        groupe.layoutBarresSelectionnees = groupeView.findViewById(R.id.layoutBarresSelectionnees);
        groupe.tvAucuneBarre = groupeView.findViewById(R.id.tvAucuneBarre);
        ImageButton btnSupprimerGroupe = groupeView.findViewById(R.id.btnSupprimerGroupe);

        // Adapteur pour le dropdown de sélection des types de barres
        ArrayAdapter<String> adapterTypesBarres = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, typesBarresDisponibles);
        groupe.autoCompleteBarreType.setAdapter(adapterTypesBarres);

        groupe.autoCompleteBarreType.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String typeSelectionne = (String) parent.getItemAtPosition(position);
                ajouterBarreSelectionnee(groupe, typeSelectionne);
                groupe.autoCompleteBarreType.setText("");
            }
        });

        if (peutEtreSupprime) {
            btnSupprimerGroupe.setVisibility(View.VISIBLE);
            btnSupprimerGroupe.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listeGroupesPoteaux.remove(groupe);
                    layoutGroupesPoteaux.removeView(groupe.rootView);
                }
            });
        } else {
            btnSupprimerGroupe.setVisibility(View.GONE);
        }

        listeGroupesPoteaux.add(groupe);
        layoutGroupesPoteaux.addView(groupeView);
    }

    private void ajouterBarreSelectionnee(GroupePoteau groupe, String typeBarre) {
        // Vérifier si le type n'est pas déjà sélectionné pour ce groupe
        if (groupe.barresSelectionneesMap.containsKey(typeBarre)) {
            Toast.makeText(this, typeBarre + " est déjà sélectionné", Toast.LENGTH_SHORT).show();
            return;
        }

        // Créer le layout pour la nouvelle barre
        LayoutInflater inflater = LayoutInflater.from(this);
        View barreView = inflater.inflate(R.layout.item_barre_selectionnee, groupe.layoutBarresSelectionnees, false);

        // Configurer les vues
        TextView tvTypeBarre = barreView.findViewById(R.id.tvTypeBarre);
        TextInputEditText etNombreBarres = barreView.findViewById(R.id.etNombreBarres);
        ImageButton btnSupprimer = barreView.findViewById(R.id.btnSupprimer);

        tvTypeBarre.setText(typeBarre);
        etNombreBarres.setText("0");

        // Stocker la référence à l'EditText
        groupe.barresSelectionneesMap.put(typeBarre, etNombreBarres);

        // Listener pour supprimer la barre
        btnSupprimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                supprimerBarreSelectionnee(groupe, typeBarre, barreView);
            }
        });

        // Ajouter la vue au layout
        groupe.layoutBarresSelectionnees.addView(barreView);

        // Masquer le message "Aucune barre"
        groupe.tvAucuneBarre.setVisibility(View.GONE);
    }

    private void supprimerBarreSelectionnee(GroupePoteau groupe, String typeBarre, View barreView) {
        groupe.barresSelectionneesMap.remove(typeBarre);
        groupe.layoutBarresSelectionnees.removeView(barreView);

        // Afficher le message "Aucune barre" si plus aucune barre n'est sélectionnée
        if (groupe.barresSelectionneesMap.isEmpty()) {
            groupe.tvAucuneBarre.setVisibility(View.VISIBLE);
        }
    }

    private void calculerBarres() {
        // 1) Valider tous les groupes avant de calculer quoi que ce soit
        for (int i = 0; i < listeGroupesPoteaux.size(); i++) {
            if (!validerGroupe(listeGroupesPoteaux.get(i), i + 1)) {
                return;
            }
        }

        // 2) Parcourir tous les groupes pour :
        //    a) afficher le détail des besoins par poteau
        //    b) regrouper TOUTES les coupes nécessaires par type de barre (tous poteaux confondus)
        StringBuilder detailPoteaux = new StringBuilder();
        Map<String, List<Double>> coupesParTypeBarre = new HashMap<>();

        for (GroupePoteau groupe : listeGroupesPoteaux) {
            int nombrePoteaux = Integer.parseInt(groupe.etNombrePoteaux.getText().toString());
            double longueurPoteaux = Double.parseDouble(groupe.etLongueurPoteaux.getText().toString());

            String libellePoteaux = String.format(Locale.FRANCE, "Poteaux de %.2f m (%d poteau%s)",
                    longueurPoteaux, nombrePoteaux, nombrePoteaux > 1 ? "x" : "");

            detailPoteaux.append("=== ").append(libellePoteaux).append(" ===\n");

            // Récupérer les barres sélectionnées avec leurs quantités pour ce groupe
            Map<String, Integer> barresParType = new HashMap<>();
            for (Map.Entry<String, TextInputEditText> entry : groupe.barresSelectionneesMap.entrySet()) {
                int nombre = getValeur(entry.getValue());
                if (nombre > 0) {
                    barresParType.put(entry.getKey(), nombre);
                }
            }

            if (barresParType.isEmpty()) {
                detailPoteaux.append("Veuillez saisir au moins un type de barre avec une quantité positive.\n\n");
                continue;
            }

            for (Map.Entry<String, Integer> entry : barresParType.entrySet()) {
                String typeBarre = entry.getKey();
                int nombreParPoteau = entry.getValue();
                int totalCoupesNecessaires = nombreParPoteau * nombrePoteaux;

                detailPoteaux.append("  • ").append(typeBarre).append(": ")
                        .append(nombreParPoteau).append(" coupes/poteau → ")
                        .append(totalCoupesNecessaires).append(" coupes au total\n");

                // Ajouter ces coupes à la liste globale du type de barre (partagée entre TOUS les poteaux)
                List<Double> coupes = coupesParTypeBarre.get(typeBarre);
                if (coupes == null) {
                    coupes = new ArrayList<>();
                    coupesParTypeBarre.put(typeBarre, coupes);
                }
                for (int i = 0; i < totalCoupesNecessaires; i++) {
                    coupes.add(longueurPoteaux);
                }
            }
            detailPoteaux.append("\n");
        }

        if (coupesParTypeBarre.isEmpty()) {
            tvResultats.setText(detailPoteaux.toString().trim());
            cardResultats.setVisibility(View.VISIBLE);
            return;
        }

        // 3) Un seul calcul d'optimisation par type de barre, sur l'ensemble des coupes
        //    demandées par TOUS les poteaux de ce type (réutilisation des chutes entre poteaux différents).
        StringBuilder achats = new StringBuilder();
        achats.append("=== Barres à acheter (optimisé sur l'ensemble du chantier) ===\n\n");

        for (Map.Entry<String, List<Double>> entry : coupesParTypeBarre.entrySet()) {
            String typeBarre = entry.getKey();
            ResultatCalcul resultat = calculerBarresNecessaires(entry.getValue());

            achats.append("• ").append(typeBarre).append(":\n");
            achats.append("  - Barres à acheter: ").append(resultat.barresNecessaires).append("\n");

            if (resultat.listeDechets.isEmpty()) {
                achats.append("  - Déchets: Aucun\n");
            } else {
                achats.append("  - Déchets: ").append(resultat.listeDechets.size()).append(" chutes\n");

                Map<Double, Integer> dechetsParLongueur = new HashMap<>();
                for (Double dechet : resultat.listeDechets) {
                    dechetsParLongueur.put(dechet, dechetsParLongueur.getOrDefault(dechet, 0) + 1);
                }

                for (Map.Entry<Double, Integer> entryDechet : dechetsParLongueur.entrySet()) {
                    if (entryDechet.getValue() == 1) {
                        achats.append("    * Chute: ")
                                .append(String.format(Locale.FRANCE, "%.2f", entryDechet.getKey())).append(" m\n");
                    } else {
                        achats.append("    * ").append(entryDechet.getValue()).append(" chutes: ")
                                .append(String.format(Locale.FRANCE, "%.2f", entryDechet.getKey())).append(" m\n");
                    }
                }
            }

            achats.append("  - Optimisé: ").append(resultat.utilisationOptimisee ? "Oui" : "Non").append("\n\n");
        }

        tvResultats.setText(detailPoteaux.toString() + achats.toString());
        cardResultats.setVisibility(View.VISIBLE);
    }

    /**
     * Calcule le nombre de barres de 12 m nécessaires pour satisfaire TOUTES les coupes demandées
     * (potentiellement de longueurs différentes) pour un même type de barre.
     * Algorithme "best-fit decreasing" : on traite les coupes de la plus longue à la plus courte,
     * et pour chaque coupe on la place sur la barre déjà entamée qui laissera le moins de perte,
     * sinon on ouvre une nouvelle barre. Cela permet de réutiliser les chutes même entre deux
     * poteaux différents qui partagent le même type de barre.
     */
    private ResultatCalcul calculerBarresNecessaires(List<Double> coupesRequises) {
        int barresNecessaires = 0;
        List<Double> barresOuvertes = new ArrayList<>(); // longueur restante sur chaque barre déjà entamée
        List<Double> listeDechets = new ArrayList<>();

        List<Double> coupesTriees = new ArrayList<>(coupesRequises);
        Collections.sort(coupesTriees, Collections.reverseOrder());

        for (double longueurCoupe : coupesTriees) {
            if (longueurCoupe <= 0) {
                continue;
            }

            // Coupe plus longue qu'une barre standard : il faut assembler plusieurs barres
            if (longueurCoupe > LONGUEUR_BARRE_STANDARD) {
                int barresParCoupe = (int) Math.ceil(longueurCoupe / LONGUEUR_BARRE_STANDARD);
                barresNecessaires += barresParCoupe;
                listeDechets.add((barresParCoupe * LONGUEUR_BARRE_STANDARD) - longueurCoupe);
                continue;
            }

            // Chercher, parmi les barres déjà entamées, celle qui laissera le moins de perte (best-fit)
            int indexTrouve = -1;
            double meilleurReste = -1;
            for (int i = 0; i < barresOuvertes.size(); i++) {
                double reste = barresOuvertes.get(i);
                if (reste >= longueurCoupe && (meilleurReste == -1 || reste < meilleurReste)) {
                    indexTrouve = i;
                    meilleurReste = reste;
                }
            }

            if (indexTrouve != -1) {
                barresOuvertes.set(indexTrouve, barresOuvertes.get(indexTrouve) - longueurCoupe);
            } else {
                barresNecessaires++;
                barresOuvertes.add(LONGUEUR_BARRE_STANDARD - longueurCoupe);
            }
        }

        // Les longueurs restant sur les barres entamées (>0) sont les déchets finaux
        for (double reste : barresOuvertes) {
            if (reste > 1e-9) {
                listeDechets.add(reste);
            }
        }

        double totalDechets = 0;
        for (double dechet : listeDechets) {
            totalDechets += dechet;
        }
        boolean utilisationOptimisee = barresNecessaires == 0
                || (totalDechets / (barresNecessaires * LONGUEUR_BARRE_STANDARD)) < 0.2;

        return new ResultatCalcul(barresNecessaires, listeDechets, utilisationOptimisee);
    }

    /**
     * Valide les champs d'un groupe de poteau donné.
     * @param numeroGroupe position du groupe (1, 2, 3...), utilisée uniquement pour identifier
     *                      le groupe dans un message d'erreur (avant calcul, on ne connaît pas
     *                      encore sa longueur).
     */
    private boolean validerGroupe(GroupePoteau groupe, int numeroGroupe) {
        if (TextUtils.isEmpty(groupe.etNombrePoteaux.getText()) ||
                TextUtils.isEmpty(groupe.etLongueurPoteaux.getText())) {
            tvResultats.setText("Poteau #" + numeroGroupe + ": veuillez remplir le nombre de poteaux et la longueur");
            cardResultats.setVisibility(View.VISIBLE);
            return false;
        }

        try {
            int nombrePoteaux = Integer.parseInt(groupe.etNombrePoteaux.getText().toString());
            double longueur = Double.parseDouble(groupe.etLongueurPoteaux.getText().toString());

            if (nombrePoteaux <= 0 || longueur <= 0) {
                tvResultats.setText("Poteau #" + numeroGroupe + ": les valeurs doivent être positives");
                cardResultats.setVisibility(View.VISIBLE);
                return false;
            }
        } catch (NumberFormatException e) {
            tvResultats.setText("Poteau #" + numeroGroupe + ": veuillez entrer des nombres valides");
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