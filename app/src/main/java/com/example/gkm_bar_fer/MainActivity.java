package com.example.gkm_bar_fer;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutGroupesPoteaux;
    private Button btnAjouterPoteau;
    private Button btnCalculer;
    private MaterialSwitch switchUtiliserStock;
    private CardView cardResultats;
    private TextView tvResultats;

    private static final double LONGUEUR_BARRE_STANDARD = ConfigurationBarres.LONGUEUR_BARRE_STANDARD;
    private List<String> typesBarresDisponibles;
    private List<GroupePoteau> listeGroupesPoteaux;

    private LinearLayout layoutResultatsBarres;

    private Button btnExporterPdf;
    private Button btnExporterExcel;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    private String dernierResumePoteaux = "";
    private LinkedHashMap<String, ResultatCalcul> derniersResultatsBarres = new LinkedHashMap<>();
    private List<String[]> derniersGroupesPoteaux = new ArrayList<>();


    // --- Espace Client ---
    public static final String EXTRA_CLIENT_ID = "client_id";
    public static final String EXTRA_CALCUL_ID = "calcul_id";

    public static final String EXTRA_TYPE_ELEMENT = "type_element";
    private TypeElement typeElement = TypeElement.POTEAU;
    private String clientId = null;
    private String calculId = null;
    private String nomCalculCharge = null;
    private Button btnEnregistrerClient;
    private JSONArray dernieresEntrees = null;
    private boolean dernierUtiliseStock = false;
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
    // Représente une coupe à réaliser, avec son origine (quel groupe de poteau l'a demandée)
    private static class CoupeRequise {
        double longueur;
        double longueurPoteau;
        int nombrePoteaux;
        String segmentInfo;

        CoupeRequise(double longueur, double longueurPoteau, int nombrePoteaux, String segmentInfo) {
            this.longueur = longueur;
            this.longueurPoteau = longueurPoteau;
            this.nombrePoteaux = nombrePoteaux;
            this.segmentInfo = segmentInfo;
        }
    }

    // Représente une barre de 12 m utilisée, avec le détail des coupes qui y sont faites
    private static class BarreUtilisee {
        List<CoupeRequise> coupes = new ArrayList<>();
        double resteDisponible;

        // Indique que le morceau provient du stock existant.
        boolean estDuStock;

        // Longueur du morceau en stock avant les coupes.
        double longueurOrigineStock;

        BarreUtilisee(double resteDisponible) {
            this.resteDisponible = resteDisponible;
        }
    }

    // Classe pour stocker les résultats de calcul

    // Petit gestionnaire de curseur d'écriture pour générer un PDF multi-pages :
// avance une position Y et ouvre automatiquement une nouvelle page si besoin.
    private static class CurseurPdf {
        PdfDocument document;
        PdfDocument.Page page;
        Canvas canvas;
        float y;
        int numeroPage;
        final int largeur;
        final int hauteur;
        final int marge;

        CurseurPdf(PdfDocument document, int largeur, int hauteur, int marge) {
            this.document = document;
            this.largeur = largeur;
            this.hauteur = hauteur;
            this.marge = marge;
            this.numeroPage = 0;
            nouvellePage();
        }

        void nouvellePage() {
            if (page != null) {
                document.finishPage(page);
            }
            numeroPage++;
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(largeur, hauteur, numeroPage).create();
            page = document.startPage(pageInfo);
            canvas = page.getCanvas();
            y = marge;
        }

        void assurerEspace(float hauteurNecessaire) {
            if (y + hauteurNecessaire > hauteur - marge) {
                nouvellePage();
            }
        }

        void ligne(String texte, Paint paint, float interligne) {
            float largeurDisponible = largeur - 2f * marge;

            for (String paragraphe : texte.split("\n", -1)) {
                if (paragraphe.isEmpty()) {
                    assurerEspace(interligne);
                    y += interligne;
                    continue;
                }

                String restant = paragraphe;

                while (!restant.isEmpty()) {
                    int nombreCaracteres = paint.breakText(
                            restant,
                            true,
                            largeurDisponible,
                            null
                    );

                    // Garantit que la boucle avance.
                    nombreCaracteres = Math.max(1, nombreCaracteres);

                    int fin = nombreCaracteres;

                    // Évite de couper un mot lorsque c'est possible.
                    if (fin < restant.length()) {
                        int dernierEspace = restant.lastIndexOf(' ', fin);

                        if (dernierEspace > 0) {
                            fin = dernierEspace;
                        }
                    }

                    assurerEspace(interligne);
                    canvas.drawText(
                            restant.substring(0, fin),
                            marge,
                            y,
                            paint
                    );
                    y += interligne;

                    restant = restant.substring(fin).trim();
                }
            }
        }

        void terminer() {
            document.finishPage(page);
        }
    }


    // Génère un fichier .xlsx minimal (format OOXML) en écrivant le XML à la main dans un ZIP,
// sans dépendance externe (ni Apache POI, ni autre librairie).
    private static class GenerateurXlsx {
        private static class Cellule {
            String texte;
            Double nombre;
            boolean gras;

            static Cellule texte(String t, boolean gras) {
                Cellule c = new Cellule();
                c.texte = t;
                c.gras = gras;
                return c;
            }

            static Cellule nombre(double n, boolean gras) {
                Cellule c = new Cellule();
                c.nombre = n;
                c.gras = gras;
                return c;
            }
        }

        private final List<String> nomsFeuilles = new ArrayList<>();
        private final List<List<List<Cellule>>> feuilles = new ArrayList<>();

        int ajouterFeuille(String nom) {
            nomsFeuilles.add(nom);
            feuilles.add(new ArrayList<List<Cellule>>());
            return feuilles.size() - 1;
        }

        void ajouterLigne(int feuille, List<Cellule> cellules) {
            feuilles.get(feuille).add(cellules);
        }

        void ecrire(File fichier) throws IOException {
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(fichier));
            try {
                ecrireEntree(zip, "[Content_Types].xml", genererContentTypes());
                ecrireEntree(zip, "_rels/.rels", genererRelsRacine());
                ecrireEntree(zip, "xl/workbook.xml", genererWorkbook());
                ecrireEntree(zip, "xl/_rels/workbook.xml.rels", genererWorkbookRels());
                ecrireEntree(zip, "xl/styles.xml", genererStyles());
                for (int i = 0; i < feuilles.size(); i++) {
                    ecrireEntree(zip, "xl/worksheets/sheet" + (i + 1) + ".xml",
                            genererFeuille(feuilles.get(i)));
                }
            } finally {
                zip.close();
            }
        }

        private void ecrireEntree(ZipOutputStream zip, String nom, String contenu) throws IOException {
            ZipEntry entree = new ZipEntry(nom);
            zip.putNextEntry(entree);
            zip.write(contenu.getBytes("UTF-8"));
            zip.closeEntry();
        }

        private String genererContentTypes() {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
            sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
            sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
            sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            for (int i = 0; i < feuilles.size(); i++) {
                sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                        .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            }
            sb.append("</Types>");
            return sb.toString();
        }

        private String genererRelsRacine() {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>";
        }

        private String genererWorkbook() {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                    .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
            sb.append("<sheets>");
            for (int i = 0; i < nomsFeuilles.size(); i++) {
                sb.append("<sheet name=\"").append(echapperXml(nomsFeuilles.get(i)))
                        .append("\" sheetId=\"").append(i + 1)
                        .append("\" r:id=\"rIdSheet").append(i + 1).append("\"/>");
            }
            sb.append("</sheets></workbook>");
            return sb.toString();
        }

        private String genererWorkbookRels() {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 0; i < feuilles.size(); i++) {
                sb.append("<Relationship Id=\"rIdSheet").append(i + 1)
                        .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
                        .append("Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
            }
            sb.append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
            sb.append("</Relationships>");
            return sb.toString();
        }

        private String genererStyles() {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"2\">"
                    + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "</fonts>"
                    + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                    + "<borders count=\"1\"><border/></borders>"
                    + "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf fontId=\"0\"/><xf fontId=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>";
        }

        private String genererFeuille(List<List<Cellule>> lignes) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            sb.append("<sheetData>");
            for (int r = 0; r < lignes.size(); r++) {
                sb.append("<row r=\"").append(r + 1).append("\">");
                List<Cellule> ligne = lignes.get(r);
                for (int c = 0; c < ligne.size(); c++) {
                    Cellule cellule = ligne.get(c);
                    String ref = colonneLettre(c) + (r + 1);
                    int style = cellule.gras ? 1 : 0;
                    if (cellule.nombre != null) {
                        sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\">")
                                .append("<v>").append(cellule.nombre).append("</v></c>");
                    } else {
                        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(style).append("\">")
                                .append("<is><t xml:space=\"preserve\">")
                                .append(echapperXml(cellule.texte == null ? "" : cellule.texte))
                                .append("</t></is></c>");
                    }
                }
                sb.append("</row>");
            }
            sb.append("</sheetData></worksheet>");
            return sb.toString();
        }

        private String colonneLettre(int index) {
            StringBuilder sb = new StringBuilder();
            int n = index;
            do {
                sb.insert(0, (char) ('A' + (n % 26)));
                n = n / 26 - 1;
            } while (n >= 0);
            return sb.toString();
        }

        private String echapperXml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&apos;");
        }
    }

    private static class ResultatCalcul {
        int barresNecessaires;
        List<Double> listeDechets;  // Liste des déchets individuels
        boolean utilisationOptimisee;
        List<BarreUtilisee> detailBarres; // Quelle(s) coupe(s), de quel poteau, sur quelle barre

        String operationStockId = UUID.randomUUID().toString();
        boolean stockConfirme = false;
        boolean dechetsEnvoyes = false;

        ResultatCalcul(int barresNecessaires, List<Double> listeDechets, boolean utilisationOptimisee,
                       List<BarreUtilisee> detailBarres) {
            this.barresNecessaires = barresNecessaires;
            this.listeDechets = listeDechets;
            this.utilisationOptimisee = utilisationOptimisee;
            this.detailBarres = detailBarres;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initTypesBarres();
        initViews();
        appliquerTypeElement();
        setupListeners();
        configurerTiroirNavigation();
        configurerModeClient();


// Premier groupe de poteau affiché par défaut, sans bouton "Supprimer"
        ajouterGroupePoteau(false);

        // Si on ouvre un calcul enregistré, on restaure ses saisies et ses résultats
        chargerCalculSiDemande();
    }

    private void initTypesBarres() {
        typesBarresDisponibles = new ArrayList<>(ConfigurationBarres.listerTypes());

        listeGroupesPoteaux = new ArrayList<>();
    }

    private void initViews() {
        layoutGroupesPoteaux = findViewById(R.id.layoutGroupesPoteaux);
        btnAjouterPoteau = findViewById(R.id.btnAjouterPoteau);
        btnCalculer = findViewById(R.id.btnCalculer);
        switchUtiliserStock = findViewById(R.id.switchUtiliserStock);
        cardResultats = findViewById(R.id.cardResultats);
        tvResultats = findViewById(R.id.tvResultats);
        layoutResultatsBarres = findViewById(R.id.layoutResultatsBarres);
        btnExporterPdf = findViewById(R.id.btnExporterPdf);
        btnExporterExcel = findViewById(R.id.btnExporterExcel);
        btnEnregistrerClient = findViewById(R.id.btnEnregistrerClient);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        switchUtiliserStock = findViewById(R.id.switchUtiliserStock);
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
        btnExporterPdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exporterResultatsEnPdf();
            }
        });
        btnExporterExcel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exporterResultatsEnExcel();
            }
        });
    }
    private void configurerTiroirNavigation() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        drawerToggle = new ActionBarDrawerToggle(
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

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                drawerLayout.closeDrawer(GravityCompat.START);

                int id = item.getItemId();
                if (id == R.id.navAccueil) {
                    startActivity(new Intent(MainActivity.this, MenuActivity.class));
                    finish();
                } else if (id == R.id.navStockDechets) {
                    startActivity(new Intent(MainActivity.this, StockActivity.class));
                } else if (id == R.id.navEspaceClient) {
                    startActivity(new Intent(MainActivity.this, EspaceClientActivity.class));
                }

                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
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

        // Libellés adaptés au type d'élément (poteau, dalle, semelle...)
        TextView tvTitreGroupe = groupeView.findViewById(R.id.tvTitreGroupe);
        tvTitreGroupe.setText("Informations des " + typeElement.getPlurielMajuscule());

        TextInputLayout layoutNombre = trouverTextInputLayout(groupe.etNombrePoteaux);
        if (layoutNombre != null) {
            layoutNombre.setHint("Nombre de " + typeElement.getPluriel());
        }
        TextInputLayout layoutLongueur = trouverTextInputLayout(groupe.etLongueurPoteaux);
        if (layoutLongueur != null) {
            layoutLongueur.setHint("Longueur de " + typeElement.getSingulier());
        }
        btnSupprimerGroupe.setContentDescription(
                "Supprimer " + typeElement.getCe() + " " + typeElement.getSingulier());

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
        groupe.tvAucuneBarre.setText("•••");
        groupe.tvAucuneBarre.setVisibility(View.VISIBLE);
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

        // Instantané des saisies au moment du calcul (utilisé pour l'enregistrement client)
        try {
            dernieresEntrees = entreesVersJson();
        } catch (JSONException e) {
            dernieresEntrees = null;
        }
        dernierUtiliseStock = switchUtiliserStock.isChecked();

        // 2) Parcourir tous les groupes pour :
        //    a) afficher le détail des besoins par poteau
        //    b) regrouper TOUTES les coupes nécessaires par type de barre (tous poteaux confondus)
        StringBuilder detailPoteaux = new StringBuilder();
        Map<String, List<CoupeRequise>> coupesParTypeBarre = new HashMap<>();
        List<String[]> nouveauxGroupesPoteaux = new ArrayList<>();

        for (GroupePoteau groupe : listeGroupesPoteaux) {
            int nombrePoteaux = Integer.parseInt(groupe.etNombrePoteaux.getText().toString());
            double longueurPoteaux = Double.parseDouble(groupe.etLongueurPoteaux.getText().toString());

            String libellePoteaux = String.format(Locale.FRANCE, "%s de %.2f m (%d %s)",
                    typeElement.getPlurielMajuscule(), longueurPoteaux, nombrePoteaux,
                    nombrePoteaux > 1 ? typeElement.getPluriel() : typeElement.getSingulier());

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
                        .append(nombreParPoteau).append(" coupes/").append(typeElement.getSingulier()).append(" → ")
                        .append(totalCoupesNecessaires).append(" coupes au total\n");

                // Ajouter ces coupes à la liste globale du type de barre (partagée entre TOUS les poteaux)
                List<CoupeRequise> coupes = coupesParTypeBarre.get(typeBarre);
                if (coupes == null) {
                    coupes = new ArrayList<>();
                    coupesParTypeBarre.put(typeBarre, coupes);
                }
                for (int i = 0; i < totalCoupesNecessaires; i++) {
                    coupes.add(new CoupeRequise(longueurPoteaux, longueurPoteaux, nombrePoteaux, null));
                }
                nouveauxGroupesPoteaux.add(new String[]{
                        String.format(Locale.FRANCE, "%.2f", longueurPoteaux),
                        String.valueOf(nombrePoteaux),
                        typeBarre,
                        String.valueOf(nombreParPoteau),
                        String.valueOf(totalCoupesNecessaires)
                });
            }
            detailPoteaux.append("\n");
        }

        if (coupesParTypeBarre.isEmpty()) {
            layoutResultatsBarres.removeAllViews();
            derniersResultatsBarres.clear();
            derniersGroupesPoteaux.clear();
            dernierResumePoteaux = "";
            tvResultats.setText(detailPoteaux.toString().trim());
            cardResultats.setVisibility(View.VISIBLE);
            return;
        }

        // 3) Un seul calcul d'optimisation par type de barre, sur l'ensemble des coupes
        //    demandées par TOUS les poteaux de ce type (réutilisation des chutes entre poteaux différents).
        // 3) Un seul calcul d'optimisation par type de barre, sur l'ensemble des coupes
        //    demandées par TOUS les poteaux de ce type (réutilisation des chutes entre poteaux différents).
        tvResultats.setText(detailPoteaux.toString()
                + "=== Optimisé sur l'ensemble du chantier ===");

        dernierResumePoteaux = detailPoteaux.toString().trim();
        derniersResultatsBarres.clear();
        derniersGroupesPoteaux = nouveauxGroupesPoteaux;

        layoutResultatsBarres.removeAllViews();
        for (Map.Entry<String, List<CoupeRequise>> entry : coupesParTypeBarre.entrySet()) {
            String typeBarre = entry.getKey();
            List<MorceauFer> morceauxDisponibles =
                    switchUtiliserStock.isChecked()
                            ? StockManager.getInstance(this).obtenirMorceaux(typeBarre)
                            : new ArrayList<>();

            ResultatCalcul resultat = calculerBarresNecessaires(
                    entry.getValue(),
                    morceauxDisponibles
            );
            derniersResultatsBarres.put(typeBarre, resultat);
            ajouterResultatBarre(typeBarre, resultat);
        }

        cardResultats.setVisibility(View.VISIBLE);
    }

    private void actualiserBoutonStock(Button bouton, boolean confirme) {
        if (confirme) {
            bouton.setText("Annuler utilisation du stock");

            bouton.setTextColor(
                    ContextCompat.getColor(this, R.color.white)
            );

            bouton.setBackgroundTintList(
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.error)
                    )
            );

        } else {
            bouton.setText("Confirmer utilisation du stock");

            bouton.setTextColor(
                    ContextCompat.getColor(this, R.color.white)
            );

            bouton.setBackgroundTintList(
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.primary)
                    )
            );
        }
    }

    /**
     * Construit et ajoute au conteneur layoutResultatsBarres le bloc de résultat d'un type de barre :
     * le résumé (barres à acheter / déchets / optimisé) toujours visible, et le détail barre par barre
     * masqué derrière un bouton "Afficher détail".
     */
    private void ajouterResultatBarre(String typeBarre, ResultatCalcul resultat) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View itemView = inflater.inflate(R.layout.item_resultat_barre, layoutResultatsBarres, false);

        TextView tvResumeBarre = itemView.findViewById(R.id.tvResumeBarre);
        Button btnAfficherDetail = itemView.findViewById(R.id.btnAfficherDetail);
        Button btnDechetsAuStock = itemView.findViewById(R.id.btnDechetsAuStock);
        Button btnConfirmerStock =
                itemView.findViewById(R.id.btnConfirmerStock);
        Button btnResumer = itemView.findViewById(R.id.btnResumer);
        TextView tvResumeBarresDetail = itemView.findViewById(R.id.tvResumeBarresDetail);
        TextView tvDetailBarres = itemView.findViewById(R.id.tvDetailBarres);

        StringBuilder resume = new StringBuilder();
        resume.append("• ").append(typeBarre).append(":\n");
        resume.append("  - Barres à acheter: ").append(resultat.barresNecessaires).append("\n");

        if (resultat.listeDechets.isEmpty()) {
            resume.append("  - Déchets: Aucun");
        } else {
            resume.append("  - Déchets: ").append(resultat.listeDechets.size()).append(" chutes\n");

            Map<Double, Integer> dechetsParLongueur = new HashMap<>();
            for (Double dechet : resultat.listeDechets) {
                dechetsParLongueur.put(dechet, dechetsParLongueur.getOrDefault(dechet, 0) + 1);
            }

            List<String> lignesDechets = new ArrayList<>();
            for (Map.Entry<Double, Integer> entryDechet : dechetsParLongueur.entrySet()) {
                if (entryDechet.getValue() == 1) {
                    lignesDechets.add("    * Chute: "
                            + String.format(Locale.FRANCE, "%.2f", entryDechet.getKey()) + " m");
                } else {
                    lignesDechets.add("    * " + entryDechet.getValue() + " chutes: "
                            + String.format(Locale.FRANCE, "%.2f", entryDechet.getKey()) + " m");
                }
            }
            resume.append(TextUtils.join("\n", lignesDechets));
            resume.append("\n");
        }
        resume.append("\n  - Optimisé: ").append(resultat.utilisationOptimisee ? "Oui" : "Non");
        tvResumeBarre.setText(resume.toString());

        StringBuilder detail = new StringBuilder();
        int numeroBarre = 1;
        for (BarreUtilisee barre : resultat.detailBarres) {
            if (numeroBarre > 1) {
                detail.append("\n");
            }
            detail.append("Barre ").append(numeroBarre).append(" : ")
                    .append(formatterDetailBarre(barre));
            numeroBarre++;
        }
        tvDetailBarres.setText(detail.toString());
        tvResumeBarresDetail.setText(formatterResumeBarres(resultat.detailBarres));

        final List<BarreUtilisee> barresStockUtilisees = new ArrayList<>();

        for (BarreUtilisee barre : resultat.detailBarres) {
            if (barre.estDuStock && !barre.coupes.isEmpty()) {
                barresStockUtilisees.add(barre);
            }
        }

        btnConfirmerStock.setVisibility(
                barresStockUtilisees.isEmpty() ? View.GONE : View.VISIBLE
        );

        btnConfirmerStock.setEnabled(true);
        actualiserBoutonStock(btnConfirmerStock, resultat.stockConfirme);

        btnConfirmerStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StockManager manager =
                        StockManager.getInstance(MainActivity.this);

                if (!resultat.stockConfirme) {
                    List<Double> origines = new ArrayList<>();
                    List<Double> restes = new ArrayList<>();

                    for (BarreUtilisee barre : barresStockUtilisees) {
                        origines.add(barre.longueurOrigineStock);

                        if (barre.resteDisponible > 1e-9) {
                            restes.add(barre.resteDisponible);
                        }
                    }

                    boolean succes = manager.confirmerUtilisationStock(
                            resultat.operationStockId,
                            typeBarre,
                            origines,
                            restes
                    );

                    if (!succes) {
                        Toast.makeText(
                                MainActivity.this,
                                "Confirmation impossible : vérifiez le stock. "
                                        + "L'opération peut aussi être déjà enregistrée "
                                        + "ou la sauvegarde avoir échoué.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    resultat.stockConfirme = true;

                    Toast.makeText(
                            MainActivity.this,
                            "Utilisation du stock confirmée pour " + typeBarre + ".",
                            Toast.LENGTH_SHORT
                    ).show();

                } else {
                    boolean succes = manager.annulerUtilisationStock(
                            resultat.operationStockId
                    );

                    if (!succes) {
                        Toast.makeText(
                                MainActivity.this,
                                "Annulation impossible : le stock a pu être modifié "
                                        + "depuis la confirmation, ou la sauvegarde "
                                        + "a échoué.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    resultat.stockConfirme = false;

                    // L'ancienne opération reste enregistrée comme annulée.
                    // Une nouvelle confirmation utilisera un nouvel identifiant.
                    resultat.operationStockId = UUID.randomUUID().toString();

                    Toast.makeText(
                            MainActivity.this,
                            "Utilisation du stock annulée pour " + typeBarre + ".",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                actualiserBoutonStock(
                        btnConfirmerStock,
                        resultat.stockConfirme
                );
            }
        });

        btnAfficherDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean estVisible = tvDetailBarres.getVisibility() == View.VISIBLE;
                tvDetailBarres.setVisibility(estVisible ? View.GONE : View.VISIBLE);
                btnResumer.setVisibility(estVisible ? View.GONE : View.VISIBLE);
                btnAfficherDetail.setText(estVisible ? "Afficher détail" : "Masquer détail");

                if (estVisible) {
                    // On replie aussi le résumé s'il était ouvert
                    tvResumeBarresDetail.setVisibility(View.GONE);
                    btnResumer.setText("Résumer");
                }
            }
        });

        btnResumer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean estVisible = tvResumeBarresDetail.getVisibility() == View.VISIBLE;
                tvResumeBarresDetail.setVisibility(estVisible ? View.GONE : View.VISIBLE);
                btnResumer.setText(estVisible ? "Résumer" : "Masquer résumé");
            }
        });

        final List<Double> dechetsBarresNeuves = new ArrayList<>();

        for (BarreUtilisee barre : resultat.detailBarres) {
            if (!barre.estDuStock && barre.resteDisponible > 1e-9) {
                dechetsBarresNeuves.add(barre.resteDisponible);
            }
        }

        btnDechetsAuStock.setVisibility(
                dechetsBarresNeuves.isEmpty() ? View.GONE : View.VISIBLE
        );


        // Calcul restauré : on retrouve l'état "chutes envoyées au stock"
        if (resultat.dechetsEnvoyes) {
            btnDechetsAuStock.setText("Retirer chutes envoyées");
            btnDechetsAuStock.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(MainActivity.this, R.color.error)));
        }





        btnDechetsAuStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!resultat.dechetsEnvoyes) {
                    StockManager.getInstance(MainActivity.this)
                            .ajouterDechets(typeBarre, dechetsBarresNeuves);
                    btnDechetsAuStock.setText("Retirer chutes envoyées");
                    btnDechetsAuStock.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(MainActivity.this, R.color.error)));
                    resultat.dechetsEnvoyes = true;
                    Toast.makeText(MainActivity.this,
                            "Déchets de " + typeBarre + " ajoutés au stock.", Toast.LENGTH_SHORT).show();
                } else {
                    StockManager.getInstance(MainActivity.this)
                            .retirerDechets(typeBarre, dechetsBarresNeuves);
                    btnDechetsAuStock.setText("Déchets au stock");
                    btnDechetsAuStock.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(MainActivity.this, R.color.success)));
                    resultat.dechetsEnvoyes = false;
                    Toast.makeText(MainActivity.this,
                            "Déchets de " + typeBarre + " retirés du stock.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        layoutResultatsBarres.addView(itemView);
    }

    /**
     * Génère un PDF récapitulatif (poteaux + barres à acheter + détail barre par barre)
     * à partir des derniers résultats calculés, puis propose de l'ouvrir/partager.
     */
    private void exporterResultatsEnPdf() {
        if (derniersResultatsBarres.isEmpty()) {
            Toast.makeText(this, "Veuillez d'abord calculer les résultats.", Toast.LENGTH_SHORT).show();
            return;
        }

        Paint paintTitre = new Paint();
        paintTitre.setTextSize(18);
        paintTitre.setFakeBoldText(true);
        paintTitre.setColor(Color.parseColor("#2196F3"));

        Paint paintDate = new Paint();
        paintDate.setTextSize(10);
        paintDate.setColor(Color.GRAY);

        Paint paintSection = new Paint();
        paintSection.setTextSize(14);
        paintSection.setFakeBoldText(true);
        paintSection.setColor(Color.BLACK);

        Paint paintTexte = new Paint();
        paintTexte.setTextSize(11);
        paintTexte.setColor(Color.DKGRAY);

        PdfDocument pdfDocument = new PdfDocument();
        CurseurPdf curseur = new CurseurPdf(pdfDocument, 595, 842, 40);

        curseur.ligne("GKM Bar Fer - Résultats de calcul (" + typeElement.getPlurielMajuscule() + ")", paintTitre, 24);
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date());
        curseur.ligne("Généré le " + date, paintDate, 22);

        for (String ligne : dernierResumePoteaux.split("\n")) {
            curseur.ligne(ligne, paintTexte, 14);
        }
        curseur.y += 10;

        curseur.ligne("Barres à acheter (optimisé sur l'ensemble du chantier)", paintSection, 22);

        for (Map.Entry<String, ResultatCalcul> entry : derniersResultatsBarres.entrySet()) {
            String typeBarre = entry.getKey();
            ResultatCalcul resultat = entry.getValue();

            curseur.assurerEspace(50);
            curseur.y += 6;
            curseur.ligne("Type de barre : " + typeBarre, paintSection, 18);
            curseur.ligne("Barres à acheter : " + resultat.barresNecessaires, paintTexte, 14);
            curseur.ligne("Optimisé : " + (resultat.utilisationOptimisee ? "Oui" : "Non"), paintTexte, 14);

            if (resultat.listeDechets.isEmpty()) {
                curseur.ligne("Déchets : Aucun", paintTexte, 14);
            } else {
                Map<Double, Integer> dechetsParLongueur = new HashMap<>();
                for (Double dechet : resultat.listeDechets) {
                    dechetsParLongueur.put(dechet, dechetsParLongueur.getOrDefault(dechet, 0) + 1);
                }
                StringBuilder dechetsTxt = new StringBuilder("Déchets : ");
                for (Map.Entry<Double, Integer> d : dechetsParLongueur.entrySet()) {
                    dechetsTxt.append(d.getValue()).append("x ")
                            .append(String.format(Locale.FRANCE, "%.2f", d.getKey())).append("m   ");
                }
                curseur.ligne(dechetsTxt.toString(), paintTexte, 14);
            }

            curseur.ligne("Détail des barres :", paintTexte, 14);
            int numeroBarre = 1;
            for (BarreUtilisee barre : resultat.detailBarres) {
                curseur.ligne("  Barre " + numeroBarre + " : " + formatterDetailBarre(barre), paintTexte, 13);
                numeroBarre++;
            }
        }

        curseur.terminer();

        try {
            File dossierBase = getExternalFilesDir(null);
            if (dossierBase == null) {
                dossierBase = getFilesDir();
            }
            File dossierExports = new File(dossierBase, "exports");
            if (!dossierExports.exists()) {
                dossierExports.mkdirs();
            }

            String nomFichier = "GKM_" + typeElement.getPlurielMajuscule() + "_" + System.currentTimeMillis() + ".pdf";
            File fichierPdf = new File(dossierExports, nomFichier);

            FileOutputStream fos = new FileOutputStream(fichierPdf);
            pdfDocument.writeTo(fos);
            fos.close();
            pdfDocument.close();

            Uri uriPdf = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", fichierPdf);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uriPdf, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(Intent.createChooser(intent, "Ouvrir le PDF avec"));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this,
                        "Aucune application pour ouvrir le PDF. Fichier enregistré : " + fichierPdf.getName(),
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Erreur lors de la génération du PDF : " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exporterResultatsEnExcel() {
        if (derniersResultatsBarres.isEmpty()) {
            Toast.makeText(this, "Veuillez d'abord calculer les résultats.", Toast.LENGTH_SHORT).show();
            return;
        }

        GenerateurXlsx classeur = new GenerateurXlsx();

        int feuillePoteaux = classeur.ajouterFeuille(typeElement.getPlurielMajuscule());
        classeur.ajouterLigne(feuillePoteaux, Arrays.asList(
                GenerateurXlsx.Cellule.texte("Longueur " + typeElement.getSingulier() + " (m)", true),
                GenerateurXlsx.Cellule.texte("Nombre de " + typeElement.getPluriel(), true),
                GenerateurXlsx.Cellule.texte("Type de barre", true),
                GenerateurXlsx.Cellule.texte("Coupes / " + typeElement.getSingulier(), true),
                GenerateurXlsx.Cellule.texte("Total coupes", true)));

        for (String[] ligne : derniersGroupesPoteaux) {
            classeur.ajouterLigne(feuillePoteaux, Arrays.asList(
                    GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[0].replace(",", ".")), false),
                    GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[1]), false),
                    GenerateurXlsx.Cellule.texte(ligne[2], false),
                    GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[3]), false),
                    GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[4]), false)));
        }

        for (Map.Entry<String, ResultatCalcul> entry : derniersResultatsBarres.entrySet()) {
            String typeBarre = entry.getKey();
            ResultatCalcul resultat = entry.getValue();

            int feuille = classeur.ajouterFeuille(typeBarre);

            classeur.ajouterLigne(feuille, Arrays.asList(
                    GenerateurXlsx.Cellule.texte("Barres à acheter", true),
                    GenerateurXlsx.Cellule.nombre(resultat.barresNecessaires, false)));
            classeur.ajouterLigne(feuille, Arrays.asList(
                    GenerateurXlsx.Cellule.texte("Optimisé", true),
                    GenerateurXlsx.Cellule.texte(resultat.utilisationOptimisee ? "Oui" : "Non", false)));

            double totalDechets = 0;
            for (double d : resultat.listeDechets) {
                totalDechets += d;
            }
            classeur.ajouterLigne(feuille, Arrays.asList(
                    GenerateurXlsx.Cellule.texte("Total déchets (m)", true),
                    GenerateurXlsx.Cellule.nombre(totalDechets, false)));

            classeur.ajouterLigne(feuille, Collections.<GenerateurXlsx.Cellule>emptyList());

            classeur.ajouterLigne(feuille, Arrays.asList(
                    GenerateurXlsx.Cellule.texte("Barre N°", true),
                    GenerateurXlsx.Cellule.texte("Coupes", true),
                    GenerateurXlsx.Cellule.texte("Chute (m)", true)));

            int numeroBarre = 1;
            for (BarreUtilisee barre : resultat.detailBarres) {
                classeur.ajouterLigne(feuille, Arrays.asList(
                        GenerateurXlsx.Cellule.nombre(numeroBarre, false),
                        GenerateurXlsx.Cellule.texte(
                                formatterDescriptionCoupes(barre) + marqueurStock(barre),
                                false
                        ),
                        GenerateurXlsx.Cellule.nombre(
                                barre.resteDisponible > 1e-9 ? barre.resteDisponible : 0.0, false)));
                numeroBarre++;
            }
        }

        try {
            File dossierBase = getExternalFilesDir(null);
            if (dossierBase == null) {
                dossierBase = getFilesDir();
            }
            File dossierExports = new File(dossierBase, "exports");
            if (!dossierExports.exists()) {
                dossierExports.mkdirs();
            }

            String nomFichier = "GKM_" + typeElement.getPlurielMajuscule() + "_" + System.currentTimeMillis() + ".xlsx";
            File fichierXlsx = new File(dossierExports, nomFichier);
            classeur.ecrire(fichierXlsx);

            Uri uriXlsx = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", fichierXlsx);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uriXlsx,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(Intent.createChooser(intent, "Ouvrir le fichier Excel avec"));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this,
                        "Aucune application pour ouvrir le fichier Excel. Fichier enregistré : "
                                + fichierXlsx.getName(),
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Erreur lors de la génération du fichier Excel : " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Calcule le nombre de barres de 12 m nécessaires pour satisfaire TOUTES les coupes demandées
     * (potentiellement de longueurs différentes) pour un même type de barre.
     * Algorithme "best-fit decreasing" : on traite les coupes de la plus longue à la plus courte,
     * et pour chaque coupe on la place sur la barre déjà entamée qui laissera le moins de perte,
     * sinon on ouvre une nouvelle barre. Cela permet de réutiliser les chutes même entre deux
     * poteaux différents qui partagent le même type de barre.
     */

    /**
     * Regroupe et met en forme les coupes d'une barre donnée, ex :
     * "2× Poteaux de 2,00 m (2,00 m) + Poteaux de 10,00 m (10,00 m)"
     */
    private String formatterDescriptionCoupes(BarreUtilisee barre) {
        LinkedHashMap<String, Integer> comptage = new LinkedHashMap<>();
        LinkedHashMap<String, CoupeRequise> exemples = new LinkedHashMap<>();
        for (CoupeRequise c : barre.coupes) {
            String cle = c.longueur + "|" + c.longueurPoteau + "|" + c.nombrePoteaux + "|" + c.segmentInfo;
            comptage.put(cle, comptage.getOrDefault(cle, 0) + 1);
            exemples.putIfAbsent(cle, c);
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : comptage.entrySet()) {
            CoupeRequise c = exemples.get(e.getKey());
            int n = e.getValue();
            String longueurCoupeTxt = String.format(Locale.FRANCE, "%.2f", c.longueur);
            String longueurPoteauTxt = String.format(Locale.FRANCE, "%.2f", c.longueurPoteau);
            String segmentTxt = c.segmentInfo != null ? " (" + c.segmentInfo + ")" : "";
            String finPhrasePoteau = c.nombrePoteaux > 1
                    ? "les " + c.nombrePoteaux + " " + typeElement.getPluriel() + " de " + longueurPoteauTxt + " m"
                    : typeElement.getLeSingulier() + " de " + longueurPoteauTxt + " m";

            parts.add(n + " coupe" + (n > 1 ? "s" : "") + " de " + longueurCoupeTxt + " m" + segmentTxt
                    + " pour " + finPhrasePoteau);
        }
        return TextUtils.join(" + ", parts);
    }

    private String marqueurStock(BarreUtilisee barre) {
        if (!barre.estDuStock) {
            return "";
        }

        return " (depuis le stock, chute initiale de "
                + String.format(
                Locale.FRANCE,
                "%.2f",
                barre.longueurOrigineStock
        )
                + " m)";
    }

    private String formatterDetailBarre(BarreUtilisee barre) {
        List<Double> chutes = barre.resteDisponible > 1e-9
                ? Collections.singletonList(barre.resteDisponible)
                : Collections.<Double>emptyList();

        return formatterDescriptionCoupes(barre)
                + " avec "
                + formatterChute(chutes)
                + marqueurStock(barre);
    }

    /**
     * Regroupe les barres identiques (même composition + même chute) en une seule ligne
     * récapitulative, ex : "3 Barres : pour chaque Barre, 1 coupe de 9,00 m pour les 5
     * poteaux de 9,00 m avec chute 3,00 m".
     */
    private String formatterResumeBarres(List<BarreUtilisee> detailBarres) {
        LinkedHashMap<String, Integer> comptage = new LinkedHashMap<>();
        for (BarreUtilisee barre : detailBarres) {
            String description = formatterDetailBarre(barre);
            comptage.put(description, comptage.getOrDefault(description, 0) + 1);
        }

        List<String> lignes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : comptage.entrySet()) {
            int nombre = entry.getValue();
            String description = entry.getKey();
            if (nombre == 1) {
                lignes.add("1 Barre : " + description);
            } else {
                lignes.add(nombre + " Barres : pour chaque Barre, " + description);
            }
        }
        return TextUtils.join("\n", lignes);
    }

    private String formatterChute(List<Double> chutes) {
        if (chutes.isEmpty()) {
            return "0 chutes";
        }
        Map<Double, Integer> comptageChutes = new LinkedHashMap<>();
        for (Double d : chutes) {
            comptageChutes.put(d, comptageChutes.getOrDefault(d, 0) + 1);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Double, Integer> e : comptageChutes.entrySet()) {
            String longueurTxt = String.format(Locale.FRANCE, "%.2f", e.getKey());
            if (e.getValue() == 1) {
                parts.add("chute " + longueurTxt + " m");
            } else {
                parts.add(e.getValue() + " chutes de " + longueurTxt + " m");
            }
        }
        return TextUtils.join(" et ", parts);
    }
    private ResultatCalcul calculerBarresNecessaires(
            List<CoupeRequise> coupesRequises,
            List<MorceauFer> morceauxDisponibles) {

        final double epsilon = 1e-9;

        // Copie de travail : aucune modification du stock réel.
        List<MorceauFer> stockDisponible = new ArrayList<>();
        for (MorceauFer morceau : morceauxDisponibles) {
            if (morceau.quantite > 0
                    && morceau.longueur > 0
                    && !Double.isNaN(morceau.longueur)
                    && !Double.isInfinite(morceau.longueur)) {

                stockDisponible.add(new MorceauFer(
                        morceau.longueur,
                        morceau.quantite
                ));
            }
        }

        // Comme dans le calcul existant, une longueur > 12 m
        // est décomposée en segments de 12 m maximum.
        List<CoupeRequise> coupesTriees = new ArrayList<>();

        for (CoupeRequise coupe : coupesRequises) {
            if (coupe.longueur <= 0
                    || Double.isNaN(coupe.longueur)
                    || Double.isInfinite(coupe.longueur)) {
                continue;
            }

            if (coupe.longueur > LONGUEUR_BARRE_STANDARD) {
                int nombreSegments = (int) Math.ceil(
                        coupe.longueur / LONGUEUR_BARRE_STANDARD
                );

                for (int i = 0; i < nombreSegments; i++) {
                    double longueurSegment = Math.min(
                            LONGUEUR_BARRE_STANDARD,
                            coupe.longueur - i * LONGUEUR_BARRE_STANDARD
                    );

                    if (longueurSegment > epsilon) {
                        coupesTriees.add(new CoupeRequise(
                                longueurSegment,
                                coupe.longueurPoteau,
                                coupe.nombrePoteaux,
                                "segment " + (i + 1) + "/" + nombreSegments
                        ));
                    }
                }
            } else {
                coupesTriees.add(coupe);
            }
        }

        // Traiter les plus grandes coupes en premier.
        Collections.sort(coupesTriees, new Comparator<CoupeRequise>() {
            @Override
            public int compare(CoupeRequise a, CoupeRequise b) {
                return Double.compare(b.longueur, a.longueur);
            }
        });

        List<BarreUtilisee> barresUtilisees = new ArrayList<>();
        int barresNecessaires = 0;

        for (CoupeRequise coupe : coupesTriees) {

            // Chercher d'abord parmi les morceaux de stock déjà entamés.
            BarreUtilisee meilleureBarreStock = trouverBarreCompatible(
                    barresUtilisees,
                    coupe.longueur,
                    true
            );

            // Chercher aussi le plus petit morceau de stock non entamé
            // capable de contenir cette coupe.
            MorceauFer meilleurMorceau = null;

            for (MorceauFer morceau : stockDisponible) {
                if (morceau.quantite > 0
                        && morceau.longueur + epsilon >= coupe.longueur
                        && (meilleurMorceau == null
                        || morceau.longueur < meilleurMorceau.longueur)) {
                    meilleurMorceau = morceau;
                }
            }

            BarreUtilisee barreChoisie = meilleureBarreStock;

            // Entre stock entamé et stock non entamé, choisir celui
            // qui laissera le moins de reste après cette coupe.
            if (meilleurMorceau != null
                    && (barreChoisie == null
                    || meilleurMorceau.longueur < barreChoisie.resteDisponible)) {

                barreChoisie = new BarreUtilisee(meilleurMorceau.longueur);
                barreChoisie.estDuStock = true;
                barreChoisie.longueurOrigineStock = meilleurMorceau.longueur;

                meilleurMorceau.quantite--;
                barresUtilisees.add(barreChoisie);
            }

            // Aucun morceau du stock ne convient :
            // essayer une barre neuve déjà ouverte.
            if (barreChoisie == null) {
                barreChoisie = trouverBarreCompatible(
                        barresUtilisees,
                        coupe.longueur,
                        false
                );
            }

            // Sinon, prévoir l'achat d'une nouvelle barre.
            if (barreChoisie == null) {
                barreChoisie = new BarreUtilisee(LONGUEUR_BARRE_STANDARD);
                barresUtilisees.add(barreChoisie);
                barresNecessaires++;
            }

            barreChoisie.coupes.add(coupe);
            barreChoisie.resteDisponible = Math.max(
                    0.0,
                    barreChoisie.resteDisponible - coupe.longueur
            );

            if (barreChoisie.resteDisponible < epsilon) {
                barreChoisie.resteDisponible = 0.0;
            }
        }

        // Tous les restes sont conservés pour l'affichage et les exports.
        List<Double> listeDechets = new ArrayList<>();
        double totalDechets = 0.0;
        double longueurTotaleUtilisee = 0.0;

        for (BarreUtilisee barre : barresUtilisees) {
            longueurTotaleUtilisee += barre.estDuStock
                    ? barre.longueurOrigineStock
                    : LONGUEUR_BARRE_STANDARD;

            if (barre.resteDisponible > epsilon) {
                listeDechets.add(barre.resteDisponible);
                totalDechets += barre.resteDisponible;
            }
        }

        // Conserve le critère existant : moins de 20 % de restes,
        // en tenant compte cette fois des longueurs issues du stock.
        boolean utilisationOptimisee = longueurTotaleUtilisee <= epsilon
                || totalDechets / longueurTotaleUtilisee < 0.2;

        return new ResultatCalcul(
                barresNecessaires,
                listeDechets,
                utilisationOptimisee,
                barresUtilisees
        );
    }

    private BarreUtilisee trouverBarreCompatible(
            List<BarreUtilisee> barres,
            double longueurCoupe,
            boolean depuisStock) {

        final double epsilon = 1e-9;
        BarreUtilisee meilleure = null;

        for (BarreUtilisee barre : barres) {
            if (barre.estDuStock != depuisStock) {
                continue;
            }

            if (barre.resteDisponible + epsilon >= longueurCoupe
                    && (meilleure == null
                    || barre.resteDisponible < meilleure.resteDisponible)) {
                meilleure = barre;
            }
        }

        return meilleure;
    }



    // ------------------------------------------------------------------
    // Mode client : enregistrement du calcul pour un client
    // ------------------------------------------------------------------

    private void configurerModeClient() {
        clientId = getIntent().getStringExtra(EXTRA_CLIENT_ID);
        calculId = getIntent().getStringExtra(EXTRA_CALCUL_ID);

        Client client = clientId == null
                ? null
                : ClientManager.getInstance(this).trouverParId(clientId);

        if (client == null) {
            clientId = null;
            btnEnregistrerClient.setVisibility(View.GONE);
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Calcul " + typeElement.getSingulier() + " - " + client.getNom());
        }

        btnEnregistrerClient.setVisibility(View.VISIBLE);
        btnEnregistrerClient.setText(calculId != null
                ? "Enregistrer les modifications"
                : "Enregistrer pour " + client.getNom());
        btnEnregistrerClient.setOnClickListener(v -> afficherDialogueEnregistrement());
    }

    private void afficherDialogueEnregistrement() {
        if (derniersResultatsBarres.isEmpty() || dernieresEntrees == null) {
            Toast.makeText(this, "Calculez d'abord avant d'enregistrer.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        View vueDialogue = LayoutInflater.from(this).inflate(R.layout.dialog_nom_calcul, null);
        final TextInputLayout layoutNom = vueDialogue.findViewById(R.id.layoutNomCalcul);
        final TextInputEditText etNom = vueDialogue.findViewById(R.id.etNomCalcul);

        if (calculId != null && nomCalculCharge != null) {
            etNom.setText(nomCalculCharge);
        } else {
            String dateDuJour = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
            etNom.setText("Calcul du " + dateDuJour);
        }
        etNom.selectAll();

        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setTitle(calculId != null ? "Enregistrer les modifications" : "Enregistrer le calcul")
                .setView(vueDialogue)
                .setNegativeButton("Ignorer", null)
                .setPositiveButton("Enregistrer", null)
                .create();

        dialogue.setOnShowListener(d ->
                dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(bouton -> {
                    String nom = etNom.getText() == null
                            ? ""
                            : etNom.getText().toString().trim();

                    if (nom.isEmpty()) {
                        layoutNom.setError("Le nom du calcul est obligatoire");
                        return;
                    }
                    layoutNom.setError(null);

                    try {
                        String json = construireJsonCalcul();
                        CalculManager gestionnaire = CalculManager.getInstance(MainActivity.this);
                        boolean etaitModification = calculId != null;
                        boolean succes;

                        if (etaitModification) {
                            succes = gestionnaire.modifier(calculId, nom, json);
                        } else {
                            CalculEnregistre cree = gestionnaire.ajouter(clientId, nom, json);
                            succes = cree != null;
                            if (cree != null) {
                                // Les enregistrements suivants mettront ce calcul à jour
                                calculId = cree.getId();
                            }
                        }

                        if (!succes) {
                            Toast.makeText(MainActivity.this,
                                    "L'enregistrement a échoué.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        nomCalculCharge = nom;
                        btnEnregistrerClient.setText("Enregistrer les modifications");

                        Toast.makeText(MainActivity.this,
                                etaitModification ? "Modifications enregistrées." : "Calcul enregistré.",
                                Toast.LENGTH_SHORT).show();
                        dialogue.dismiss();
                    } catch (JSONException e) {
                        Toast.makeText(MainActivity.this,
                                "Impossible d'enregistrer ce calcul.", Toast.LENGTH_LONG).show();
                    }
                })
        );

        dialogue.show();
    }

    // ------------------------------------------------------------------
    // Sérialisation JSON du calcul (saisies + résultats)
    // ------------------------------------------------------------------

    private JSONArray entreesVersJson() throws JSONException {
        JSONArray groupes = new JSONArray();

        for (GroupePoteau groupe : listeGroupesPoteaux) {
            JSONObject jg = new JSONObject();
            jg.put("nombrePoteaux", groupe.etNombrePoteaux.getText() == null
                    ? "" : groupe.etNombrePoteaux.getText().toString());
            jg.put("longueurPoteaux", groupe.etLongueurPoteaux.getText() == null
                    ? "" : groupe.etLongueurPoteaux.getText().toString());

            JSONArray barres = new JSONArray();
            for (Map.Entry<String, TextInputEditText> entry : groupe.barresSelectionneesMap.entrySet()) {
                JSONObject jb = new JSONObject();
                jb.put("type", entry.getKey());
                jb.put("quantite", entry.getValue().getText() == null
                        ? "" : entry.getValue().getText().toString());
                barres.put(jb);
            }
            jg.put("barres", barres);

            groupes.put(jg);
        }

        return groupes;
    }

    private JSONObject resultatVersJson(ResultatCalcul resultat) throws JSONException {
        JSONObject jr = new JSONObject();
        jr.put("barresNecessaires", resultat.barresNecessaires);
        jr.put("utilisationOptimisee", resultat.utilisationOptimisee);
        jr.put("operationStockId", resultat.operationStockId);
        jr.put("stockConfirme", resultat.stockConfirme);
        jr.put("dechetsEnvoyes", resultat.dechetsEnvoyes);

        JSONArray dechets = new JSONArray();
        for (Double dechet : resultat.listeDechets) {
            dechets.put(dechet.doubleValue());
        }
        jr.put("listeDechets", dechets);

        JSONArray barres = new JSONArray();
        for (BarreUtilisee barre : resultat.detailBarres) {
            JSONObject jb = new JSONObject();
            jb.put("resteDisponible", barre.resteDisponible);
            jb.put("estDuStock", barre.estDuStock);
            jb.put("longueurOrigineStock", barre.longueurOrigineStock);

            JSONArray coupes = new JSONArray();
            for (CoupeRequise coupe : barre.coupes) {
                JSONObject jc = new JSONObject();
                jc.put("longueur", coupe.longueur);
                jc.put("longueurPoteau", coupe.longueurPoteau);
                jc.put("nombrePoteaux", coupe.nombrePoteaux);
                jc.put("segmentInfo", coupe.segmentInfo == null
                        ? JSONObject.NULL : coupe.segmentInfo);
                coupes.put(jc);
            }
            jb.put("coupes", coupes);

            barres.put(jb);
        }
        jr.put("detailBarres", barres);

        return jr;
    }

    private String construireJsonCalcul() throws JSONException {
        JSONObject racine = new JSONObject();
        racine.put("version", 1);
        racine.put("typeElement", typeElement.getCode());
        racine.put("utiliserStock", dernierUtiliseStock);
        racine.put("entrees", dernieresEntrees);
        racine.put("texteResultats", tvResultats.getText().toString());
        racine.put("resumePoteaux", dernierResumePoteaux);

        JSONArray groupesPoteaux = new JSONArray();
        for (String[] ligne : derniersGroupesPoteaux) {
            JSONArray jl = new JSONArray();
            for (String valeur : ligne) {
                jl.put(valeur);
            }
            groupesPoteaux.put(jl);
        }
        racine.put("groupesPoteaux", groupesPoteaux);

        JSONArray resultats = new JSONArray();
        for (Map.Entry<String, ResultatCalcul> entry : derniersResultatsBarres.entrySet()) {
            JSONObject jr = new JSONObject();
            jr.put("type", entry.getKey());
            jr.put("resultat", resultatVersJson(entry.getValue()));
            resultats.put(jr);
        }
        racine.put("resultats", resultats);

        return racine.toString();
    }

    // ------------------------------------------------------------------
    // Ouverture d'un calcul enregistré (restauration des saisies et résultats)
    // ------------------------------------------------------------------

    private void chargerCalculSiDemande() {
        if (clientId == null || calculId == null) {
            calculId = null;
            return;
        }

        CalculEnregistre calcul = CalculManager.getInstance(this).trouverParId(calculId);
        if (calcul == null || !calcul.getClientId().equals(clientId)) {
            calculId = null;
            Toast.makeText(this, "Calcul introuvable.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject racine = new JSONObject(calcul.getDonneesJson());
            restaurerEntrees(racine.optJSONArray("entrees"));
            switchUtiliserStock.setChecked(racine.optBoolean("utiliserStock", false));
            restaurerResultats(racine);
            nomCalculCharge = calcul.getNom();
        } catch (JSONException e) {
            calculId = null;
            Toast.makeText(this, "Ce calcul enregistré est illisible.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void restaurerEntrees(JSONArray entrees) throws JSONException {
        if (entrees == null || entrees.length() == 0) {
            return;
        }

        layoutGroupesPoteaux.removeAllViews();
        listeGroupesPoteaux.clear();

        for (int i = 0; i < entrees.length(); i++) {
            JSONObject jg = entrees.getJSONObject(i);

            // Le premier groupe n'a pas de bouton "Supprimer"
            ajouterGroupePoteau(i > 0);
            GroupePoteau groupe = listeGroupesPoteaux.get(listeGroupesPoteaux.size() - 1);

            groupe.etNombrePoteaux.setText(jg.optString("nombrePoteaux", ""));
            groupe.etLongueurPoteaux.setText(jg.optString("longueurPoteaux", ""));

            JSONArray barres = jg.optJSONArray("barres");
            if (barres == null) {
                continue;
            }
            for (int j = 0; j < barres.length(); j++) {
                JSONObject jb = barres.getJSONObject(j);
                String type = jb.getString("type");

                ajouterBarreSelectionnee(groupe, type);

                TextInputEditText etQuantite = groupe.barresSelectionneesMap.get(type);
                if (etQuantite != null) {
                    etQuantite.setText(jb.optString("quantite", ""));
                }
            }
        }
    }

    private void restaurerResultats(JSONObject racine) throws JSONException {
        dernieresEntrees = racine.optJSONArray("entrees");
        dernierUtiliseStock = racine.optBoolean("utiliserStock", false);
        dernierResumePoteaux = racine.optString("resumePoteaux", "");

        derniersGroupesPoteaux = new ArrayList<>();
        JSONArray groupesPoteaux = racine.optJSONArray("groupesPoteaux");
        if (groupesPoteaux != null) {
            for (int i = 0; i < groupesPoteaux.length(); i++) {
                JSONArray jl = groupesPoteaux.getJSONArray(i);
                String[] ligne = new String[jl.length()];
                for (int k = 0; k < jl.length(); k++) {
                    ligne[k] = jl.getString(k);
                }
                derniersGroupesPoteaux.add(ligne);
            }
        }

        derniersResultatsBarres.clear();
        layoutResultatsBarres.removeAllViews();

        JSONArray resultats = racine.optJSONArray("resultats");
        if (resultats != null) {
            for (int i = 0; i < resultats.length(); i++) {
                JSONObject jr = resultats.getJSONObject(i);
                String type = jr.getString("type");
                ResultatCalcul resultat = resultatDepuisJson(jr.getJSONObject("resultat"));

                derniersResultatsBarres.put(type, resultat);
                ajouterResultatBarre(type, resultat);
            }
        }

        tvResultats.setText(racine.optString("texteResultats", ""));
        cardResultats.setVisibility(View.VISIBLE);
    }

    private ResultatCalcul resultatDepuisJson(JSONObject jr) throws JSONException {
        List<Double> dechets = new ArrayList<>();
        JSONArray jd = jr.optJSONArray("listeDechets");
        if (jd != null) {
            for (int i = 0; i < jd.length(); i++) {
                dechets.add(jd.getDouble(i));
            }
        }

        List<BarreUtilisee> barres = new ArrayList<>();
        JSONArray jbs = jr.optJSONArray("detailBarres");
        if (jbs != null) {
            for (int i = 0; i < jbs.length(); i++) {
                JSONObject jb = jbs.getJSONObject(i);

                BarreUtilisee barre = new BarreUtilisee(jb.optDouble("resteDisponible", 0.0));
                barre.estDuStock = jb.optBoolean("estDuStock", false);
                barre.longueurOrigineStock = jb.optDouble("longueurOrigineStock", 0.0);

                JSONArray jcs = jb.optJSONArray("coupes");
                if (jcs != null) {
                    for (int k = 0; k < jcs.length(); k++) {
                        JSONObject jc = jcs.getJSONObject(k);
                        String segment = jc.isNull("segmentInfo")
                                ? null
                                : jc.optString("segmentInfo", null);

                        barre.coupes.add(new CoupeRequise(
                                jc.getDouble("longueur"),
                                jc.getDouble("longueurPoteau"),
                                jc.getInt("nombrePoteaux"),
                                segment
                        ));
                    }
                }

                barres.add(barre);
            }
        }

        ResultatCalcul resultat = new ResultatCalcul(
                jr.getInt("barresNecessaires"),
                dechets,
                jr.optBoolean("utilisationOptimisee", false),
                barres
        );
        resultat.operationStockId =
                jr.optString("operationStockId", UUID.randomUUID().toString());
        resultat.stockConfirme = jr.optBoolean("stockConfirme", false);
        resultat.dechetsEnvoyes = jr.optBoolean("dechetsEnvoyes", false);

        return resultat;
    }

    // ------------------------------------------------------------------
    // Type d'élément (poteau, dalle, semelle...) : adaptation des libellés
    // ------------------------------------------------------------------

    private void appliquerTypeElement() {
        typeElement = TypeElement.depuisCode(getIntent().getStringExtra(EXTRA_TYPE_ELEMENT));

        btnAjouterPoteau.setText(
                "Ajouter " + typeElement.getUnAutre() + " " + typeElement.getSingulier());
    }

    /** Retrouve le TextInputLayout qui contient une zone de saisie. */
    private TextInputLayout trouverTextInputLayout(View vue) {
        android.view.ViewParent parent = vue.getParent();
        while (parent != null) {
            if (parent instanceof TextInputLayout) {
                return (TextInputLayout) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    /**
     * Valide les champs d'un groupe d'élément (poteau, dalle, semelle...).
     * @param numeroGroupe position du groupe (1, 2, 3...), utilisée uniquement pour identifier
     *                      le groupe dans un message d'erreur (avant calcul, on ne connaît pas
     *                      encore sa longueur).
     */
    private boolean validerGroupe(GroupePoteau groupe, int numeroGroupe) {
        if (TextUtils.isEmpty(groupe.etNombrePoteaux.getText()) ||
                TextUtils.isEmpty(groupe.etLongueurPoteaux.getText())) {
            tvResultats.setText(typeElement.getSingulierMajuscule() + " #" + numeroGroupe
                    + ": veuillez remplir le nombre de " + typeElement.getPluriel() + " et la longueur");
            cardResultats.setVisibility(View.VISIBLE);
            return false;
        }

        try {
            int nombrePoteaux = Integer.parseInt(groupe.etNombrePoteaux.getText().toString());
            double longueur = Double.parseDouble(groupe.etLongueurPoteaux.getText().toString());

            if (nombrePoteaux <= 0 || longueur <= 0) {
                tvResultats.setText(typeElement.getSingulierMajuscule() + " #" + numeroGroupe
                        + ": les valeurs doivent être positives");
                cardResultats.setVisibility(View.VISIBLE);
                return false;
            }
        } catch (NumberFormatException e) {
            tvResultats.setText(typeElement.getSingulierMajuscule() + " #" + numeroGroupe
                    + ": veuillez entrer des nombres valides");
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