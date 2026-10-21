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
import android.content.res.Configuration;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.AlertDialog;
import android.widget.PopupMenu;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Rect;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

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
    public static final String TYPE_MULTI = "multi";
    private boolean modeMulti = false;
    private TypeElement typeElement = TypeElement.POTEAU;
    private String clientId = null;
    private String nomClient = null;
    private String calculId = null;
    private String nomCalculCharge = null;
    private Button btnEnregistrerClient;
    private JSONArray dernieresEntrees = null;
    private boolean dernierUtiliseStock = false;
    // Représente un "type de poteau" : ses infos (nombre/longueur) + les barres qui lui sont associées.
    private static class GroupePoteau {
        View rootView;
        TypeElement type;
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
        TypeElement type;

        CoupeRequise(double longueur, double longueurPoteau, int nombrePoteaux,
                     String segmentInfo, TypeElement type) {
            this.longueur = longueur;
            this.longueurPoteau = longueurPoteau;
            this.nombrePoteaux = nombrePoteaux;
            this.segmentInfo = segmentInfo;
            this.type = type;
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


// Premier groupe affiché par défaut, sans bouton "Supprimer".
        // En mode Multi-Calcul, le type est d'abord choisi dans un modal.
        if (modeMulti) {
            afficherChoixTypeMulti();
        } else {
            ajouterGroupePoteau(false);
        }

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
                if (modeMulti) {
                    afficherSelecteurAjoutMulti();
                } else {
                    ajouterGroupePoteau(true);
                }
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
                } else if (id == R.id.navParametres) {
                    // Écran de calcul : changer la langue le redémarre, on prévient l'utilisateur.
                    ParametresDialog.afficher(MainActivity.this, true);
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
        ajouterGroupePoteau(peutEtreSupprime, typeElement);
    }

    private void ajouterGroupePoteau(boolean peutEtreSupprime, TypeElement type) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View groupeView = inflater.inflate(R.layout.item_groupe_poteau, layoutGroupesPoteaux, false);

        final GroupePoteau groupe = new GroupePoteau();
        groupe.rootView = groupeView;
        groupe.type = type;
        groupe.etNombrePoteaux = groupeView.findViewById(R.id.etNombrePoteaux);
        groupe.etLongueurPoteaux = groupeView.findViewById(R.id.etLongueurPoteaux);
        groupe.autoCompleteBarreType = groupeView.findViewById(R.id.autoCompleteBarreType);
        groupe.layoutBarresSelectionnees = groupeView.findViewById(R.id.layoutBarresSelectionnees);
        groupe.tvAucuneBarre = groupeView.findViewById(R.id.tvAucuneBarre);
        ImageButton btnSupprimerGroupe = groupeView.findViewById(R.id.btnSupprimerGroupe);

        // Libellés adaptés au type d'élément (poteau, dalle, semelle...)
        TextView tvTitreGroupe = groupeView.findViewById(R.id.tvTitreGroupe);
        tvTitreGroupe.setText(type.infos(this));

        TextInputLayout layoutNombre = trouverTextInputLayout(groupe.etNombrePoteaux);
        if (layoutNombre != null) {
            layoutNombre.setHint(type.nombre(this));
        }
        TextInputLayout layoutLongueur = trouverTextInputLayout(groupe.etLongueurPoteaux);
        if (layoutLongueur != null) {
            layoutLongueur.setHint(type.longueur(this));
        }
        btnSupprimerGroupe.setContentDescription(type.supprimer(this));

        // Adapteur pour le dropdown de sélection des types de barres
        ArrayAdapter<String> adapterTypesBarres = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, typesBarresDisponibles);
        groupe.autoCompleteBarreType.setAdapter(adapterTypesBarres);

        groupe.autoCompleteBarreType.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String typeSelectionne = (String) parent.getItemAtPosition(position);
                ajouterBarreSelectionnee(groupe, typeSelectionne, true);
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

    /**
     * Ajoute une ligne "type de barre + quantité" au groupe.
     * @param focaliser true quand l'utilisateur vient de choisir le type : on l'amène directement
     *                  sur la quantité (défilement + curseur + clavier numérique). false lors de la
     *                  restauration d'un calcul enregistré, pour ne pas ouvrir le clavier.
     */
    private void ajouterBarreSelectionnee(GroupePoteau groupe, String typeBarre, boolean focaliser) {
        // Vérifier si le type n'est pas déjà sélectionné pour ce groupe
        if (groupe.barresSelectionneesMap.containsKey(typeBarre)) {
            Toast.makeText(this, getString(R.string.barre_deja_selectionnee, typeBarre), Toast.LENGTH_SHORT).show();
            if (focaliser) {
                // On amène l'utilisateur sur la quantité de la ligne qui existe déjà.
                focaliserQuantite(groupe.barresSelectionneesMap.get(typeBarre));
            }
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

        // Touche "Terminé" du clavier : on referme le clavier et on quitte le champ.
        etNombreBarres.setOnEditorActionListener((vue, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(vue.getWindowToken(), 0);
                }
                vue.clearFocus();
                return true;
            }
            return false;
        });

        // Le type vient d'être choisi : on passe directement à la saisie de la quantité.
        if (focaliser) {
            focaliserQuantite(etNombreBarres);
        }
    }

    /**
     * Amène l'utilisateur sur un champ "Quantité" : la ligne est rendue visible, le champ prend le
     * focus avec son contenu sélectionné (taper remplace le "0") et le clavier numérique s'ouvre.
     */
    private void focaliserQuantite(final TextInputEditText champ) {
        if (champ == null) {
            return;
        }

        // Petit délai : laisse le menu déroulant se fermer et la nouvelle ligne se dessiner.
        champ.postDelayed(() -> {
            if (!champ.isAttachedToWindow()) {
                return; // ligne supprimée entre-temps
            }

            champ.requestRectangleOnScreen(
                    new Rect(0, 0, champ.getWidth(), champ.getHeight()), false);

            champ.requestFocus();
            champ.setSelection(champ.length()); // curseur à la fin, rien de sélectionné

            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(champ, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
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

            String libellePoteaux = getString(R.string.entete_groupe,
                    groupe.type.plurielDefiniMajuscule(this),
                    String.format(Locale.FRANCE, "%.2f", longueurPoteaux),
                    groupe.type.compte(this, nombrePoteaux));

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
                detailPoteaux.append(getString(R.string.erreur_au_moins_une_barre));
                continue;
            }

            for (Map.Entry<String, Integer> entry : barresParType.entrySet()) {
                String typeBarre = entry.getKey();
                int nombreParPoteau = entry.getValue();
                int totalCoupesNecessaires = nombreParPoteau * nombrePoteaux;

                detailPoteaux.append(getString(R.string.ligne_besoin,
                        typeBarre,
                        getResources().getQuantityString(R.plurals.nb_coupes, nombreParPoteau, nombreParPoteau),
                        groupe.type.singulier(this),
                        getResources().getQuantityString(R.plurals.nb_coupes,
                                totalCoupesNecessaires, totalCoupesNecessaires)));

                // Ajouter ces coupes à la liste globale du type de barre (partagée entre TOUS les poteaux)
                List<CoupeRequise> coupes = coupesParTypeBarre.get(typeBarre);
                if (coupes == null) {
                    coupes = new ArrayList<>();
                    coupesParTypeBarre.put(typeBarre, coupes);
                }
                for (int i = 0; i < totalCoupesNecessaires; i++) {
                    coupes.add(new CoupeRequise(longueurPoteaux, longueurPoteaux, nombrePoteaux, null, groupe.type));
                }
                nouveauxGroupesPoteaux.add(new String[]{
                        String.format(Locale.FRANCE, "%.2f", longueurPoteaux),
                        String.valueOf(nombrePoteaux),
                        typeBarre,
                        String.valueOf(nombreParPoteau),
                        String.valueOf(totalCoupesNecessaires),
                        groupe.type.getCode()
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
                + getString(R.string.titre_optimise_chantier));

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
            bouton.setText(R.string.bouton_annuler_stock);

            bouton.setTextColor(
                    ContextCompat.getColor(this, R.color.white)
            );

            bouton.setBackgroundTintList(
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.error)
                    )
            );

        } else {
            bouton.setText(R.string.confirmer_utilisation_stock);

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
        resume.append(getString(R.string.resume_titre_type, typeBarre));
        resume.append(getString(R.string.resume_barres_acheter, resultat.barresNecessaires));

        // Stock activé : quantité prélevée sur le stock, avec le détail des longueurs
        if (dernierUtiliseStock) {
            resume.append(getString(R.string.resume_quantite_stock,
                    formatterQuantiteStock(resultat, this)));
        }

        if (resultat.listeDechets.isEmpty()) {
            resume.append(getString(R.string.resume_dechets_aucun));
        } else {
            int nbDechets = resultat.listeDechets.size();
            resume.append(getString(R.string.resume_dechets_nombre,
                    getResources().getQuantityString(R.plurals.nb_chutes, nbDechets, nbDechets)));

            Map<Double, Integer> dechetsParLongueur = new HashMap<>();
            for (Double dechet : resultat.listeDechets) {
                dechetsParLongueur.put(dechet, dechetsParLongueur.getOrDefault(dechet, 0) + 1);
            }

            List<String> lignesDechets = new ArrayList<>();
            for (Map.Entry<Double, Integer> entryDechet : dechetsParLongueur.entrySet()) {
                String longueurTxt = String.format(Locale.FRANCE, "%.2f", entryDechet.getKey());
                int n = entryDechet.getValue();
                if (n == 1) {
                    lignesDechets.add(getString(R.string.resume_chute_unique, longueurTxt));
                } else {
                    lignesDechets.add(getResources().getQuantityString(
                            R.plurals.resume_chutes_multiples, n, n, longueurTxt));
                }
            }
            resume.append(TextUtils.join("\n", lignesDechets));
            resume.append("\n");
        }
        resume.append(getString(R.string.resume_optimise,
                getString(resultat.utilisationOptimisee ? R.string.oui : R.string.non)));
        tvResumeBarre.setText(resume.toString());

        StringBuilder detail = new StringBuilder();
        int numeroBarre = 1;
        for (BarreUtilisee barre : resultat.detailBarres) {
            if (numeroBarre > 1) {
                detail.append("\n");
            }
            detail.append(getString(R.string.barre_numero, numeroBarre, formatterDetailBarre(barre, this)));
            numeroBarre++;
        }
        tvDetailBarres.setText(detail.toString());
        tvResumeBarresDetail.setText(formatterResumeBarres(resultat.detailBarres, this));

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
                            restes,
                            nomClient
                    );

                    if (!succes) {
                        Toast.makeText(
                                MainActivity.this,
                                MainActivity.this.getString(R.string.stock_confirmation_impossible),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    resultat.stockConfirme = true;

                    Toast.makeText(
                            MainActivity.this,
                            MainActivity.this.getString(R.string.stock_utilisation_confirmee, typeBarre),
                            Toast.LENGTH_SHORT
                    ).show();

                } else {
                    boolean succes = manager.annulerUtilisationStock(
                            resultat.operationStockId
                    );

                    if (!succes) {
                        Toast.makeText(
                                MainActivity.this,
                                MainActivity.this.getString(R.string.stock_annulation_impossible),
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
                            MainActivity.this.getString(R.string.stock_utilisation_annulee, typeBarre),
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
                btnAfficherDetail.setText(estVisible ? R.string.btn_afficher_detail : R.string.btn_masquer_detail);

                if (estVisible) {
                    // On replie aussi le résumé s'il était ouvert
                    tvResumeBarresDetail.setVisibility(View.GONE);
                    btnResumer.setText(R.string.resumer);
                }
            }
        });

        btnResumer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean estVisible = tvResumeBarresDetail.getVisibility() == View.VISIBLE;
                tvResumeBarresDetail.setVisibility(estVisible ? View.GONE : View.VISIBLE);
                btnResumer.setText(estVisible ? R.string.resumer : R.string.btn_masquer_resume);
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
            btnDechetsAuStock.setText(R.string.btn_retirer_chutes);
            btnDechetsAuStock.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(MainActivity.this, R.color.error)));
        }





        btnDechetsAuStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!resultat.dechetsEnvoyes) {
                    StockManager.getInstance(MainActivity.this)
                            .ajouterDechets(typeBarre, dechetsBarresNeuves, nomClient);
                    btnDechetsAuStock.setText(R.string.btn_retirer_chutes);
                    btnDechetsAuStock.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(MainActivity.this, R.color.error)));
                    resultat.dechetsEnvoyes = true;
                    Toast.makeText(MainActivity.this,
                            MainActivity.this.getString(R.string.dechets_ajoutes, typeBarre), Toast.LENGTH_SHORT).show();
                } else {
                    StockManager.getInstance(MainActivity.this)
                            .retirerDechets(typeBarre, dechetsBarresNeuves, nomClient);
                    btnDechetsAuStock.setText(R.string.dechets_au_stock);
                    btnDechetsAuStock.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(MainActivity.this, R.color.success)));
                    resultat.dechetsEnvoyes = false;
                    Toast.makeText(MainActivity.this,
                            MainActivity.this.getString(R.string.dechets_retires, typeBarre), Toast.LENGTH_SHORT).show();
                }
            }
        });

        layoutResultatsBarres.addView(itemView);
    }

    /**
     * Génère le rapport PDF professionnel (en-tête avec le nom du client, chiffres clés,
     * besoins, approvisionnement, plan de coupe, pied de page avec logo Fer Pro),
     * puis propose de l'ouvrir/partager.
     */
    private void exporterResultatsEnPdf() {
        if (derniersResultatsBarres.isEmpty()) {
            Toast.makeText(this, R.string.calculer_d_abord, Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument pdfDocument = new PdfDocument();
        RapportPdf.generer(pdfDocument, construireDonneesRapport());

        try {
            File dossierBase = getExternalFilesDir(null);
            if (dossierBase == null) {
                dossierBase = getFilesDir();
            }
            File dossierExports = new File(dossierBase, "exports");
            if (!dossierExports.exists()) {
                dossierExports.mkdirs();
            }

            String partieClient = (nomClient == null || nomClient.trim().isEmpty())
                    ? ""
                    : "_" + nomClient.trim().replaceAll("[^\\p{L}\\p{N}]+", "_");
            String nomFichier = "FerPro_" + libelleTypeExport(contexteFrancais()) + partieClient
                    + "_" + System.currentTimeMillis() + ".pdf";
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
                startActivity(Intent.createChooser(intent, getString(R.string.ouvrir_pdf_avec)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this,
                        getString(R.string.aucune_app_pdf, fichierPdf.getName()),
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.erreur_pdf, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Transforme les derniers résultats calculés en données simples pour {@link RapportPdf}.
     */
    private RapportPdf.Donnees construireDonneesRapport() {
        RapportPdf.Donnees donnees = new RapportPdf.Donnees();
        donnees.nomClient = nomClient;
        final Context fr = contexteFrancais();   // le PDF reste en français pour l'instant
        donnees.typeSingulier = typeElement.singulier(fr);
        donnees.typePluriel = typeElement.plurielDefini(fr);
        if (modeMulti) {
            // En Multi-Calcul, l'en-tête du PDF affiche "Multi-Calcul" ; chaque ligne de besoin
            // porte son propre type d'élément.
            donnees.typeSingulier = "élément";
            donnees.typePluriel = fr.getString(R.string.multi_calcul);
        }
        donnees.date = new SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(new Date());
        donnees.longueurBarreStandard = LONGUEUR_BARRE_STANDARD;
        donnees.logo = RapportPdf.chargerLogo(this);

        // Besoins : une ligne = {longueur, nombre, type de barre, coupes/élément, total des coupes}
        for (String[] ligne : derniersGroupesPoteaux) {
            if (ligne.length < 5) {
                continue;
            }
            try {
                RapportPdf.Besoin besoin = new RapportPdf.Besoin();
                besoin.longueur = Double.parseDouble(ligne[0].replace(',', '.'));
                besoin.nombre = Integer.parseInt(ligne[1]);
                besoin.typeFer = ligne[2];
                besoin.coupesParElement = Integer.parseInt(ligne[3]);
                besoin.totalCoupes = Integer.parseInt(ligne[4]);
                TypeElement typeLigne = typeDeLigne(ligne);
                besoin.typeSingulier = typeLigne.singulier(fr);
                besoin.typePluriel = typeLigne.plurielDefini(fr);
                donnees.besoins.add(besoin);
            } catch (NumberFormatException ignore) {
                // ligne illisible : ignorée
            }
        }

        // Barres par type de fer
        for (Map.Entry<String, ResultatCalcul> entry : derniersResultatsBarres.entrySet()) {
            ResultatCalcul resultat = entry.getValue();

            RapportPdf.GroupeBarres groupe = new RapportPdf.GroupeBarres();
            groupe.typeFer = entry.getKey();
            groupe.barresAcheter = resultat.barresNecessaires;
            if (dernierUtiliseStock) {
                groupe.quantiteStock = formatterQuantiteStock(resultat, fr);
            }

            for (BarreUtilisee barre : resultat.detailBarres) {
                RapportPdf.BarreDetail detail = new RapportPdf.BarreDetail();
                detail.depuisStock = barre.estDuStock;
                detail.longueurTotale = barre.estDuStock
                        ? barre.longueurOrigineStock
                        : LONGUEUR_BARRE_STANDARD;
                for (CoupeRequise coupe : barre.coupes) {
                    detail.coupes.add(coupe.longueur);
                }
                detail.chute = barre.resteDisponible;
                detail.affectation = formatterDescriptionCoupes(barre, fr) + marqueurStock(barre, fr);
                groupe.barres.add(detail);
            }
            donnees.groupes.add(groupe);
        }

        return donnees;
    }

    private void exporterResultatsEnExcel() {
        if (derniersResultatsBarres.isEmpty()) {
            Toast.makeText(this, R.string.calculer_d_abord, Toast.LENGTH_SHORT).show();
            return;
        }

        GenerateurXlsx classeur = new GenerateurXlsx();
        final Context fr = contexteFrancais();   // l'Excel reste en français pour l'instant

        final boolean multi = modeMulti;
        int feuillePoteaux = classeur.ajouterFeuille(libelleTypeExport(fr));

        List<GenerateurXlsx.Cellule> entete = new ArrayList<>();
        if (multi) {
            entete.add(GenerateurXlsx.Cellule.texte("Type d'élément", true));
        }
        entete.add(GenerateurXlsx.Cellule.texte(
                multi ? "Longueur (m)" : "Longueur " + typeElement.singulier(fr) + " (m)", true));
        entete.add(GenerateurXlsx.Cellule.texte(
                multi ? "Nombre d'éléments" : "Nombre de " + typeElement.plurielDefini(fr), true));
        entete.add(GenerateurXlsx.Cellule.texte("Type de barre", true));
        entete.add(GenerateurXlsx.Cellule.texte(
                multi ? "Coupes / élément" : "Coupes / " + typeElement.singulier(fr), true));
        entete.add(GenerateurXlsx.Cellule.texte("Total coupes", true));
        classeur.ajouterLigne(feuillePoteaux, entete);

        for (String[] ligne : derniersGroupesPoteaux) {
            List<GenerateurXlsx.Cellule> cellules = new ArrayList<>();
            if (multi) {
                cellules.add(GenerateurXlsx.Cellule.texte(
                        typeDeLigne(ligne).singulierMajuscule(fr), false));
            }
            cellules.add(GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[0].replace(",", ".")), false));
            cellules.add(GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[1]), false));
            cellules.add(GenerateurXlsx.Cellule.texte(ligne[2], false));
            cellules.add(GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[3]), false));
            cellules.add(GenerateurXlsx.Cellule.nombre(Double.parseDouble(ligne[4]), false));
            classeur.ajouterLigne(feuillePoteaux, cellules);
        }

        for (Map.Entry<String, ResultatCalcul> entry : derniersResultatsBarres.entrySet()) {
            String typeBarre = entry.getKey();
            ResultatCalcul resultat = entry.getValue();

            int feuille = classeur.ajouterFeuille(typeBarre);

            classeur.ajouterLigne(feuille, Arrays.asList(
                    GenerateurXlsx.Cellule.texte("Barres à acheter", true),
                    GenerateurXlsx.Cellule.nombre(resultat.barresNecessaires, false)));
            if (dernierUtiliseStock) {
                classeur.ajouterLigne(feuille, Arrays.asList(
                        GenerateurXlsx.Cellule.texte("Quantité prélevée sur le stock", true),
                        GenerateurXlsx.Cellule.texte(formatterQuantiteStock(resultat, fr), false)));
            }
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
                                formatterDescriptionCoupes(barre, fr) + marqueurStock(barre, fr),
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

            String nomFichier = "GKM_" + libelleTypeExport(fr) + "_" + System.currentTimeMillis() + ".xlsx";
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
                startActivity(Intent.createChooser(intent, getString(R.string.ouvrir_excel_avec)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this,
                        getString(R.string.aucune_app_excel, fichierXlsx.getName()),
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.erreur_excel, e.getMessage()),
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
     * Regroupe et met en forme les coupes d'une barre donnée, dans la langue du Context, ex :
     * "2 coupes de 2,00 m pour le poteau de 2,00 m + 1 coupe de 10,00 m pour les 3 poteaux de 10,00 m"
     */
    private String formatterDescriptionCoupes(BarreUtilisee barre, Context ctx) {
        LinkedHashMap<String, Integer> comptage = new LinkedHashMap<>();
        LinkedHashMap<String, CoupeRequise> exemples = new LinkedHashMap<>();
        for (CoupeRequise c : barre.coupes) {
            String cle = c.longueur + "|" + c.longueurPoteau + "|" + c.nombrePoteaux + "|" + c.segmentInfo
                    + "|" + c.type.getCode();
            comptage.put(cle, comptage.getOrDefault(cle, 0) + 1);
            exemples.putIfAbsent(cle, c);
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : comptage.entrySet()) {
            CoupeRequise c = exemples.get(e.getKey());
            int n = e.getValue();
            String nbCoupes = ctx.getResources().getQuantityString(R.plurals.nb_coupes, n, n);
            String longueurCoupeTxt = String.format(Locale.FRANCE, "%.2f", c.longueur);
            String longueurPoteauTxt = String.format(Locale.FRANCE, "%.2f", c.longueurPoteau);
            String segmentTxt = c.segmentInfo != null
                    ? ctx.getString(R.string.segment_info, extraireSegment(c.segmentInfo))
                    : "";

            if (c.nombrePoteaux > 1) {
                parts.add(ctx.getString(R.string.coupes_pour_plusieurs, nbCoupes, longueurCoupeTxt,
                        segmentTxt, c.type.compteGenitif(ctx, c.nombrePoteaux), longueurPoteauTxt));
            } else {
                parts.add(ctx.getString(R.string.coupes_pour_un, nbCoupes, longueurCoupeTxt,
                        segmentTxt, c.type.defini(ctx), longueurPoteauTxt));
            }
        }
        return TextUtils.join(" + ", parts);
    }

    /**
     * Quantité prélevée sur le stock, avec le détail des longueurs, dans la langue du Context.
     * ex : "3 morceaux (2 × 5,00 m + 1 × 3,20 m)" ; "0 morceau" si rien n'a été prélevé.
     */
    private String formatterQuantiteStock(ResultatCalcul resultat, Context ctx) {
        TreeMap<Long, Integer> parLongueur = new TreeMap<>(Collections.reverseOrder()); // cm -> nombre
        int total = 0;

        for (BarreUtilisee barre : resultat.detailBarres) {
            if (barre.estDuStock && !barre.coupes.isEmpty()) {
                total++;
                long cm = Math.round(barre.longueurOrigineStock * 100.0);
                Integer n = parLongueur.get(cm);
                parLongueur.put(cm, n == null ? 1 : n + 1);
            }
        }

        String nombre = ctx.getResources().getQuantityString(
                R.plurals.nb_morceaux_stock, total, total);
        if (total == 0) {
            return nombre;
        }

        List<String> parties = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : parLongueur.entrySet()) {
            parties.add(ctx.getString(R.string.stock_morceau_longueur,
                    e.getValue(),
                    String.format(Locale.FRANCE, "%.2f", e.getKey() / 100.0)));
        }
        return nombre + " (" + TextUtils.join(" + ", parties) + ")";
    }

    private String marqueurStock(BarreUtilisee barre, Context ctx) {
        if (!barre.estDuStock) {
            return "";
        }

        return ctx.getString(R.string.marqueur_stock,
                String.format(Locale.FRANCE, "%.2f", barre.longueurOrigineStock));
    }

    private String formatterDetailBarre(BarreUtilisee barre, Context ctx) {
        List<Double> chutes = barre.resteDisponible > 1e-9
                ? Collections.singletonList(barre.resteDisponible)
                : Collections.<Double>emptyList();

        return ctx.getString(R.string.detail_barre,
                formatterDescriptionCoupes(barre, ctx),
                formatterChute(chutes, ctx),
                marqueurStock(barre, ctx));
    }

    /**
     * Regroupe les barres identiques (même composition + même chute) en une seule ligne
     * récapitulative, ex : "3 Barres : pour chaque Barre, 1 coupe de 9,00 m pour les 5
     * poteaux de 9,00 m avec chute 3,00 m".
     */
    private String formatterResumeBarres(List<BarreUtilisee> detailBarres, Context ctx) {
        LinkedHashMap<String, Integer> comptage = new LinkedHashMap<>();
        for (BarreUtilisee barre : detailBarres) {
            String description = formatterDetailBarre(barre, ctx);
            comptage.put(description, comptage.getOrDefault(description, 0) + 1);
        }

        List<String> lignes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : comptage.entrySet()) {
            int nombre = entry.getValue();
            String description = entry.getKey();
            if (nombre == 1) {
                lignes.add(ctx.getString(R.string.resume_barre_une, description));
            } else {
                lignes.add(ctx.getResources().getQuantityString(
                        R.plurals.resume_barres_identiques, nombre, nombre, description));
            }
        }
        return TextUtils.join("\n", lignes);
    }

    private String formatterChute(List<Double> chutes, Context ctx) {
        if (chutes.isEmpty()) {
            return ctx.getString(R.string.chute_aucune);
        }
        Map<Double, Integer> comptageChutes = new LinkedHashMap<>();
        for (Double d : chutes) {
            comptageChutes.put(d, comptageChutes.getOrDefault(d, 0) + 1);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Double, Integer> e : comptageChutes.entrySet()) {
            String longueurTxt = String.format(Locale.FRANCE, "%.2f", e.getKey());
            int n = e.getValue();
            if (n == 1) {
                parts.add(ctx.getString(R.string.chute_une, longueurTxt));
            } else {
                parts.add(ctx.getResources().getQuantityString(R.plurals.chutes_de, n, n, longueurTxt));
            }
        }
        return TextUtils.join(ctx.getString(R.string.liaison_et), parts);
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
                                "segment " + (i + 1) + "/" + nombreSegments,
                                coupe.type
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
        nomClient = client.getNom();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titreCalculClient(client.getNom()));
        }

        btnEnregistrerClient.setVisibility(View.VISIBLE);
        btnEnregistrerClient.setText(calculId != null
                ? getString(R.string.btn_enregistrer_modifs)
                : getString(R.string.btn_enregistrer_pour, client.getNom()));
        btnEnregistrerClient.setOnClickListener(v -> afficherDialogueEnregistrement());
    }

    private void afficherDialogueEnregistrement() {
        if (derniersResultatsBarres.isEmpty() || dernieresEntrees == null) {
            Toast.makeText(this, R.string.calculer_avant_enregistrer,
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
            etNom.setText(getString(R.string.nom_calcul_defaut, dateDuJour));
        }
        etNom.selectAll();

        final AlertDialog dialogue = new AlertDialog.Builder(this)
                .setTitle(calculId != null ? R.string.btn_enregistrer_modifs : R.string.dialog_titre_enregistrer)
                .setView(vueDialogue)
                .setNegativeButton(R.string.dialog_ignorer, null)
                .setPositiveButton(R.string.dialog_enregistrer, null)
                .create();

        dialogue.setOnShowListener(d ->
                dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(bouton -> {
                    String nom = etNom.getText() == null
                            ? ""
                            : etNom.getText().toString().trim();

                    if (nom.isEmpty()) {
                        layoutNom.setError(getString(R.string.nom_calcul_obligatoire));
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
                                    MainActivity.this.getString(R.string.enregistrement_echoue), Toast.LENGTH_LONG).show();
                            return;
                        }

                        nomCalculCharge = nom;
                        btnEnregistrerClient.setText(R.string.btn_enregistrer_modifs);

                        Toast.makeText(MainActivity.this,
                                MainActivity.this.getString(etaitModification ? R.string.modifs_enregistrees : R.string.calcul_enregistre),
                                Toast.LENGTH_SHORT).show();
                        dialogue.dismiss();
                    } catch (JSONException e) {
                        Toast.makeText(MainActivity.this,
                                MainActivity.this.getString(R.string.impossible_enregistrer), Toast.LENGTH_LONG).show();
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
            jg.put("type", groupe.type.getCode());
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
                jc.put("type", coupe.type.getCode());
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
        racine.put("multi", modeMulti);
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
            Toast.makeText(this, R.string.calcul_introuvable, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject racine = new JSONObject(calcul.getDonneesJson());
            if (racine.optBoolean("multi", false)) {
                modeMulti = true;
                btnAjouterPoteau.setText(R.string.multi_ajouter_autre);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(titreCalculClient(nomClient));
                }
            }
            restaurerEntrees(racine.optJSONArray("entrees"));
            switchUtiliserStock.setChecked(racine.optBoolean("utiliserStock", false));
            restaurerResultats(racine);
            nomCalculCharge = calcul.getNom();
        } catch (JSONException e) {
            calculId = null;
            Toast.makeText(this, R.string.calcul_illisible,
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
            TypeElement typeGroupe = TypeElement.depuisCode(
                    jg.optString("type", typeElement.getCode()));
            ajouterGroupePoteau(i > 0, typeGroupe);
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

                ajouterBarreSelectionnee(groupe, type, false);

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

        tvResultats.setText(construireTexteBesoins());
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
                                segment,
                                TypeElement.depuisCode(jc.optString("type", typeElement.getCode()))
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
        String codeType = getIntent().getStringExtra(EXTRA_TYPE_ELEMENT);
        modeMulti = TYPE_MULTI.equals(codeType);
        typeElement = TypeElement.depuisCode(codeType);

        if (modeMulti) {
            btnAjouterPoteau.setText(R.string.multi_ajouter_autre);
        } else {
            btnAjouterPoteau.setText(typeElement.ajouter(this));
        }
    }

    // ------------------------------------------------------------------
    // Multi-Calcul : choix du type au démarrage
    // ------------------------------------------------------------------

    private static final TypeElement[] TYPES_MULTI = {
            TypeElement.POTEAU, TypeElement.POUTRE, TypeElement.DALLE,
            TypeElement.SEMELLE, TypeElement.PRE_POTEAU
    };

    private void afficherChoixTypeMulti() {
        String[] libelles = new String[TYPES_MULTI.length];
        for (int i = 0; i < TYPES_MULTI.length; i++) {
            libelles[i] = TYPES_MULTI[i].singulierMajuscule(this);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.multi_choisir_type_titre)
                .setItems(libelles, (dialog, which) -> appliquerChoixMulti(TYPES_MULTI[which]))
                .setNegativeButton(R.string.multi_annuler, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void appliquerChoixMulti(TypeElement choisi) {
        typeElement = choisi;
        btnAjouterPoteau.setText(R.string.multi_ajouter_autre);

        if (nomClient != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titreCalculClient(nomClient));
        }

        ajouterGroupePoteau(false, choisi);
    }

    private void afficherSelecteurAjoutMulti() {
        PopupMenu menu = new PopupMenu(this, btnAjouterPoteau);
        for (int i = 0; i < TYPES_MULTI.length; i++) {
            menu.getMenu().add(0, i, i, TYPES_MULTI[i].singulierMajuscule(this));
        }
        menu.setOnMenuItemClickListener(item -> {
            ajouterGroupePoteau(true, TYPES_MULTI[item.getItemId()]);
            return true;
        });
        menu.show();
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
            tvResultats.setText(getString(R.string.erreur_remplir_champs,
                    groupe.type.singulierMajuscule(this), numeroGroupe, groupe.type.plurielDefini(this)));
            cardResultats.setVisibility(View.VISIBLE);
            return false;
        }

        try {
            int nombrePoteaux = Integer.parseInt(groupe.etNombrePoteaux.getText().toString());
            double longueur = Double.parseDouble(groupe.etLongueurPoteaux.getText().toString());

            if (nombrePoteaux <= 0 || longueur <= 0) {
                tvResultats.setText(getString(R.string.erreur_valeurs_positives,
                        groupe.type.singulierMajuscule(this), numeroGroupe));
                cardResultats.setVisibility(View.VISIBLE);
                return false;
            }
        } catch (NumberFormatException e) {
            tvResultats.setText(getString(R.string.erreur_nombres_valides,
                    groupe.type.singulierMajuscule(this), numeroGroupe));
            cardResultats.setVisibility(View.VISIBLE);
            return false;
        }

        return true;
    }

    /** Type d'élément d'une ligne de besoin (6e champ) ; anciens calculs : type du calcul. */
    private TypeElement typeDeLigne(String[] ligne) {
        return ligne.length > 5 ? TypeElement.depuisCode(ligne[5]) : typeElement;
    }

    /** "Poutres" en calcul normal, "Multi-Calcul" en mode Multi (noms de fichiers et de feuille). */
    private String libelleTypeExport(Context ctx) {
        return modeMulti
                ? ctx.getString(R.string.multi_calcul)
                : typeElement.plurielDefiniMajuscule(ctx);
    }

    private String titreCalculClient(String nom) {
        return modeMulti
                ? getString(R.string.multi_titre_client, nom)
                : typeElement.titreCalcul(this, nom);
    }

    /** Contexte en français : sert aux PDF et Excel, qui restent en français pour l'instant. */
    private Context contexteFrancais() {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.FRENCH);
        return createConfigurationContext(configuration);
    }

    /** "segment 1/2" (anciens calculs) ou "1/2" -> "1/2". */
    private static String extraireSegment(String info) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+/\\d+").matcher(info);
        return m.find() ? m.group() : info;
    }

    /**
     * Reconstruit, dans la langue actuelle, le texte des besoins ("=== Poteaux de ... ===")
     * à partir des données du calcul. Sert à l'ouverture d'un calcul enregistré : le texte
     * n'est plus figé dans la langue où il a été enregistré.
     */
    private String construireTexteBesoins() {
        StringBuilder texte = new StringBuilder();
        String dernierGroupe = null;

        for (String[] ligne : derniersGroupesPoteaux) {
            if (ligne.length < 5) {
                continue;
            }
            try {
                int nombre = Integer.parseInt(ligne[1]);
                int parElement = Integer.parseInt(ligne[3]);
                int total = Integer.parseInt(ligne[4]);

                TypeElement typeLigne = ligne.length > 5
                        ? TypeElement.depuisCode(ligne[5])
                        : typeElement;

                String cleGroupe = ligne[0] + "|" + ligne[1] + "|" + typeLigne.getCode();
                if (!cleGroupe.equals(dernierGroupe)) {
                    if (dernierGroupe != null) {
                        texte.append("\n");
                    }
                    texte.append("=== ")
                            .append(getString(R.string.entete_groupe,
                                    typeLigne.plurielDefiniMajuscule(this),
                                    ligne[0],
                                    typeLigne.compte(this, nombre)))
                            .append(" ===\n");
                    dernierGroupe = cleGroupe;
                }

                texte.append(getString(R.string.ligne_besoin,
                        ligne[2],
                        getResources().getQuantityString(R.plurals.nb_coupes, parElement, parElement),
                        typeLigne.singulier(this),
                        getResources().getQuantityString(R.plurals.nb_coupes, total, total)));
            } catch (NumberFormatException ignore) {
                // ligne illisible : ignorée
            }
        }

        if (texte.length() > 0) {
            texte.append("\n");
        }
        texte.append(getString(R.string.titre_optimise_chantier));
        return texte.toString();
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