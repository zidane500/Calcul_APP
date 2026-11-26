package com.example.gkm_bar_fer.scan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gkm_bar_fer.ConfigurationBarres;
import com.example.gkm_bar_fer.MainActivity;
import com.example.gkm_bar_fer.R;
import com.example.gkm_bar_fer.TypeElement;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scan d'un plan de béton armé (PDF ou photo) : lecture du texte sur le téléphone (aucune donnée
 * envoyée), puis écran de VÉRIFICATION. Rien n'est importé dans un calcul sans que l'utilisateur
 * ait coché, relu et complété chaque groupe ; l'import est refusé tant qu'une valeur est vide ou
 * invalide.
 */
public class ScanPlanActivity extends AppCompatActivity {

    private static final int[] ROTATIONS = {0, 90, 270};
    private static final int SCORE_SUFFISANT = 8;
    private static final int COULEUR_PRIMAIRE = Color.parseColor("#173B65");
    private static final int COULEUR_OK = Color.parseColor("#15803D");
    private static final int COULEUR_ATTENTION = Color.parseColor("#B45309");
    private static final int COULEUR_ERREUR = Color.parseColor("#B91C1C");
    private static final int COULEUR_GRIS = Color.parseColor("#6B7280");

    private static final TypeElementScan[] TYPES_CHOIX = {
            TypeElementScan.INCONNU, TypeElementScan.POTEAU, TypeElementScan.PRE_POTEAU,
            TypeElementScan.SEMELLE, TypeElementScan.POUTRE, TypeElementScan.CHAINAGE,
            TypeElementScan.DALLE};

    private final ExecutorService executeur = Executors.newSingleThreadExecutor();
    private volatile boolean annule = false;

    private final ActivityResultLauncher<String[]> selecteur = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::surFichierChoisi);

    private ScrollView defilement;
    private LinearLayout contenu;
    private LinearLayout zoneCartes;
    private MaterialButton btnImporter;
    private TextView tvEtat;

    private List<String> typesBarres;
    private Set<String> designationsAutorisees;
    private final List<CarteGroupe> cartes = new ArrayList<>();

    // ------------------------------------------------------------------
    // Écran
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        typesBarres = ConfigurationBarres.listerTypes();
        designationsAutorisees = new HashSet<>(typesBarres);

        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar barre = new MaterialToolbar(this);
        barre.setBackgroundColor(COULEUR_PRIMAIRE);
        barre.setTitleTextColor(Color.WHITE);
        barre.setTitle(R.string.scan_titre);
        barre.setNavigationIcon(R.drawable.ic_arrow_back);
        barre.setNavigationOnClickListener(v -> finish());
        barre.setMinimumHeight(dp(56));
        racine.addView(barre, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        defilement = new ScrollView(this);
        defilement.setFillViewport(true);
        contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        contenu.setPadding(dp(16), dp(16), dp(16), dp(24));
        defilement.addView(contenu, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        racine.addView(defilement, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        btnImporter = new MaterialButton(this);
        btnImporter.setText(R.string.scan_bouton_importer);
        btnImporter.setAllCaps(false);
        btnImporter.setVisibility(View.GONE);
        btnImporter.setOnClickListener(v -> importer());
        LinearLayout.LayoutParams lpImport = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpImport.setMargins(dp(16), dp(8), dp(16), dp(12));
        racine.addView(btnImporter, lpImport);

        setContentView(racine);
        afficherAccueil();
    }

    @Override
    protected void onDestroy() {
        annule = true;
        executeur.shutdownNow();
        super.onDestroy();
    }

    private int dp(int valeur) {
        return Math.round(valeur * getResources().getDisplayMetrics().density);
    }

    private TextView texte(String contenuTexte, int taille, int couleur, boolean gras) {
        TextView tv = new TextView(this);
        tv.setText(contenuTexte);
        tv.setTextSize(taille);
        tv.setTextColor(couleur);
        if (gras) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return tv;
    }

    private LinearLayout.LayoutParams plein(int haut) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(haut);
        return lp;
    }

    private void afficherAccueil() {
        annule = false;
        contenu.removeAllViews();
        cartes.clear();
        btnImporter.setVisibility(View.GONE);

        contenu.addView(texte(getString(R.string.scan_intro), 15, Color.DKGRAY, false), plein(0));

        MaterialButton choisir = new MaterialButton(this);
        choisir.setText(R.string.scan_choisir);
        choisir.setAllCaps(false);
        choisir.setOnClickListener(v -> selecteur.launch(new String[]{"application/pdf", "image/*"}));
        contenu.addView(choisir, plein(24));
    }

    // ------------------------------------------------------------------
    // Choix du fichier et des pages
    // ------------------------------------------------------------------

    private void surFichierChoisi(Uri uri) {
        if (uri == null) {
            return;
        }
        String type = getContentResolver().getType(uri);
        boolean pdf = "application/pdf".equals(type)
                || uri.toString().toLowerCase(Locale.ROOT).endsWith(".pdf");

        if (!pdf) {
            lancerAnalyse(uri, false, java.util.Collections.singletonList(1));
            return;
        }

        int nbPages;
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
             PdfRenderer rendu = new PdfRenderer(pfd)) {
            nbPages = rendu.getPageCount();
        } catch (Exception e) {
            Toast.makeText(this, R.string.scan_erreur_lecture, Toast.LENGTH_LONG).show();
            return;
        }
        if (nbPages == 1) {
            lancerAnalyse(uri, true, java.util.Collections.singletonList(1));
            return;
        }
        demanderPages(uri, nbPages);
    }

    private void demanderPages(Uri uri, int nbPages) {
        EditText saisie = new EditText(this);
        saisie.setInputType(InputType.TYPE_CLASS_TEXT);
        saisie.setHint(R.string.scan_pages_hint);
        saisie.setText(nbPages <= 30 ? "1-" + nbPages : "1-30");
        saisie.setSelectAllOnFocus(true);

        LinearLayout cadre = new LinearLayout(this);
        cadre.setPadding(dp(20), dp(8), dp(20), 0);
        cadre.addView(saisie, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialogue = new AlertDialog.Builder(this)
                .setTitle(R.string.scan_pages_titre)
                .setMessage(getString(R.string.scan_pages_info, nbPages, SelectionPages.MAX_PAGES))
                .setView(cadre)
                .setNegativeButton(R.string.scan_annuler, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialogue.setOnShowListener(d -> dialogue.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    List<Integer> pages = SelectionPages.analyser(saisie.getText().toString(), nbPages);
                    if (pages == null) {
                        saisie.setError(getString(R.string.scan_pages_invalides, nbPages,
                                SelectionPages.MAX_PAGES));
                        return;
                    }
                    dialogue.dismiss();
                    lancerAnalyse(uri, true, pages);
                }));
        dialogue.show();
    }

    // ------------------------------------------------------------------
    // Analyse (en arrière-plan)
    // ------------------------------------------------------------------

    private void lancerAnalyse(Uri uri, boolean pdf, List<Integer> pages) {
        annule = false;
        contenu.removeAllViews();
        cartes.clear();
        btnImporter.setVisibility(View.GONE);

        ProgressBar progression = new ProgressBar(this);
        progression.setIndeterminate(true);
        LinearLayout.LayoutParams lp = plein(48);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        contenu.addView(progression, lp);

        tvEtat = texte("", 15, Color.DKGRAY, false);
        tvEtat.setGravity(Gravity.CENTER);
        contenu.addView(tvEtat, plein(16));

        MaterialButton btnAnnuler = new MaterialButton(this);
        btnAnnuler.setText(R.string.scan_annuler);
        btnAnnuler.setAllCaps(false);
        btnAnnuler.setOnClickListener(v -> {
            annule = true;
            afficherAccueil();
        });
        contenu.addView(btnAnnuler, plein(24));

        executeur.execute(() -> analyser(uri, pdf, pages));
    }

    private void analyser(Uri uri, boolean pdf, List<Integer> pages) {
        TextRecognizer lecteur = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        List<ElementDetecte> tous = new ArrayList<>();
        List<Integer> pagesVides = new ArrayList<>();
        try {
            if (pdf) {
                try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                     PdfRenderer rendu = new PdfRenderer(pfd)) {
                    for (int i = 0; i < pages.size() && !annule; i++) {
                        int numero = pages.get(i);
                        publierEtat(numero, i + 1, pages.size());
                        Bitmap image = rendrePage(rendu, numero - 1);
                        lirePage(lecteur, image, numero, tous, pagesVides);
                        image.recycle();
                    }
                }
            } else {
                publierEtat(1, 1, 1);
                Bitmap image = decoderImage(uri);
                lirePage(lecteur, image, 1, tous, pagesVides);
                image.recycle();
            }
        } catch (Exception e) {
            if (!annule) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.scan_erreur_lecture, Toast.LENGTH_LONG).show();
                    afficherAccueil();
                });
            }
            return;
        } finally {
            lecteur.close();
        }

        if (annule) {
            return;
        }
        List<ElementDetecte> elements = AnalysePlan.fusionner(tous);
        int nbPages = pages.size();
        runOnUiThread(() -> afficherResultats(elements, nbPages, pagesVides));
    }

    private void publierEtat(int numero, int rang, int total) {
        runOnUiThread(() -> {
            if (tvEtat != null) {
                tvEtat.setText(getString(R.string.scan_analyse, numero, rang, total));
            }
        });
    }

    /** Lit la page dans plusieurs sens de rotation et garde celui où le plus d'armatures sont reconnues. */
    private void lirePage(TextRecognizer lecteur, Bitmap image, int numero,
                          List<ElementDetecte> tous, List<Integer> pagesVides) throws Exception {
        List<String> meilleur = new ArrayList<>();
        int meilleurScore = -1;
        for (int rotation : ROTATIONS) {
            if (annule) {
                return;
            }
            Text texte = Tasks.await(lecteur.process(InputImage.fromBitmap(image, rotation)));
            List<String> lignes = new ArrayList<>();
            for (Text.TextBlock bloc : texte.getTextBlocks()) {
                for (Text.Line ligne : bloc.getLines()) {
                    lignes.add(ligne.getText());
                }
            }
            int score = AnalysePlan.scorer(lignes);
            if (score > meilleurScore) {
                meilleurScore = score;
                meilleur = lignes;
            }
            if (score >= SCORE_SUFFISANT) {
                break;
            }
        }
        AnalysePlan.ResultatAnalyse resultat = AnalysePlan.analyser(meilleur, numero);
        if (resultat.elements.isEmpty()) {
            pagesVides.add(numero);
        } else {
            tous.addAll(resultat.elements);
        }
    }

    private Bitmap rendrePage(PdfRenderer rendu, int index) {
        PdfRenderer.Page page = rendu.openPage(index);
        try {
            float echelle = Math.min(4f, 4000f / Math.max(page.getWidth(), page.getHeight()));
            int largeur = Math.max(1, Math.round(page.getWidth() * echelle));
            int hauteur = Math.max(1, Math.round(page.getHeight() * echelle));
            Bitmap image = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888);
            image.eraseColor(Color.WHITE);
            page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return image;
        } finally {
            page.close();
        }
    }

    private Bitmap decoderImage(Uri uri) throws IOException {
        BitmapFactory.Options mesure = new BitmapFactory.Options();
        mesure.inJustDecodeBounds = true;
        try (InputStream flux = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(flux, null, mesure);
        }
        int grand = Math.max(mesure.outWidth, mesure.outHeight);
        int echantillon = 1;
        while (grand / echantillon > 4000) {
            echantillon *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = echantillon;
        try (InputStream flux = getContentResolver().openInputStream(uri)) {
            Bitmap image = BitmapFactory.decodeStream(flux, null, options);
            if (image == null) {
                throw new IOException("Image illisible");
            }
            return image;
        }
    }

    // ------------------------------------------------------------------
    // Écran de vérification
    // ------------------------------------------------------------------

    private void afficherResultats(List<ElementDetecte> elements, int nbPages, List<Integer> pagesVides) {
        contenu.removeAllViews();
        cartes.clear();

        if (elements.isEmpty()) {
            contenu.addView(texte(getString(R.string.scan_rien), 15, COULEUR_ERREUR, false), plein(0));
            MaterialButton autre = new MaterialButton(this);
            autre.setText(R.string.scan_choisir);
            autre.setAllCaps(false);
            autre.setOnClickListener(v -> afficherAccueil());
            contenu.addView(autre, plein(24));
            return;
        }

        List<GroupeDetecte> groupes = new ArrayList<>();
        for (ElementDetecte e : elements) {
            groupes.addAll(e.versGroupes());
        }
        contenu.addView(texte(getString(R.string.scan_resume, groupes.size(), nbPages),
                15, Color.DKGRAY, true), plein(0));
        if (!pagesVides.isEmpty()) {
            contenu.addView(texte(getString(R.string.scan_pages_vides, joindre(pagesVides)),
                    13, COULEUR_GRIS, false), plein(6));
        }

        zoneCartes = new LinearLayout(this);
        zoneCartes.setOrientation(LinearLayout.VERTICAL);
        contenu.addView(zoneCartes, plein(8));

        ElementDetecte precedent = null;
        for (GroupeDetecte g : groupes) {
            ajouterCarte(g, g.origine != precedent);
            precedent = g.origine;
        }

        MaterialButton ajouter = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        ajouter.setText(R.string.scan_ajouter_groupe);
        ajouter.setAllCaps(false);
        ajouter.setOnClickListener(v -> ajouterCarte(groupeVide(), true));
        contenu.addView(ajouter, plein(16));

        btnImporter.setVisibility(View.VISIBLE);
        defilement.scrollTo(0, 0);
    }

    private String joindre(List<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        for (int p : pages) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private GroupeDetecte groupeVide() {
        ElementDetecte vide = new ElementDetecte(getString(R.string.scan_groupe_ajoute),
                TypeElementScan.INCONNU, null, "", new ArrayList<BarreDetectee>(),
                new ArrayList<Alerte>(), ElementDetecte.Confiance.HAUTE, 0, "");
        return new GroupeDetecte(vide.repere, TypeElementScan.INCONNU, null, null,
                new ArrayList<BarreDetectee>(), vide);
    }

    /** Une carte = un groupe à relire : case à cocher, type, nombre, longueur, barres. */
    private final class CarteGroupe {
        View racine;
        CheckBox coche;
        Spinner choixType;
        EditText saisieNombre;
        EditText saisieLongueur;
        LinearLayout layoutBarres;
        TextView problemes;
        boolean confirmationRequise;
        boolean confirme;
        boolean ignorer;

        ControleImport.GroupeSaisi lire() {
            ControleImport.GroupeSaisi g = new ControleImport.GroupeSaisi();
            g.type = TYPES_CHOIX[choixType.getSelectedItemPosition()];
            g.libelle = coche.getText().toString();
            g.nombre = saisieNombre.getText().toString();
            g.longueur = saisieLongueur.getText().toString();
            for (int i = 0; i < layoutBarres.getChildCount(); i++) {
                LinearLayout ligne = (LinearLayout) layoutBarres.getChildAt(i);
                Spinner designation = (Spinner) ligne.getChildAt(0);
                EditText quantite = (EditText) ligne.getChildAt(1);
                g.barres.add(new ControleImport.LigneBarre(
                        String.valueOf(designation.getSelectedItem()), quantite.getText().toString()));
            }
            return g;
        }

        void afficherProblemes(List<ControleImport.Probleme> bloquants,
                               List<ControleImport.Probleme> avertissements) {
            StringBuilder sb = new StringBuilder();
            for (ControleImport.Probleme p : bloquants) {
                sb.append("✖ ").append(getString(libelleProbleme(p))).append('\n');
            }
            for (ControleImport.Probleme p : avertissements) {
                sb.append("⚠ ").append(getString(libelleProbleme(p))).append('\n');
            }
            problemes.setText(sb.toString().trim());
            problemes.setTextColor(bloquants.isEmpty() ? COULEUR_ATTENTION : COULEUR_ERREUR);
            problemes.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        }
    }

    private int libelleProbleme(ControleImport.Probleme p) {
        switch (p) {
            case TYPE_MANQUANT:
                return R.string.scan_pb_type;
            case NOMBRE_INVALIDE:
                return R.string.scan_pb_nombre;
            case LONGUEUR_INVALIDE:
                return R.string.scan_pb_longueur;
            case AUCUNE_BARRE:
                return R.string.scan_pb_aucune_barre;
            case BARRE_INCONNUE:
                return R.string.scan_pb_barre_inconnue;
            case QUANTITE_INVALIDE:
                return R.string.scan_pb_quantite;
            default:
                return R.string.scan_pb_longueur_12;
        }
    }

    private void ajouterCarte(GroupeDetecte g, boolean premierDeLElement) {
        final CarteGroupe carte = new CarteGroupe();
        ElementDetecte origine = g.origine;

        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);
        racine.setPadding(dp(14), dp(12), dp(14), dp(12));
        racine.setBackgroundColor(Color.parseColor("#F3F6FA"));
        LinearLayout.LayoutParams lpCarte = plein(12);
        carte.racine = racine;

        // --- En-tête : case à cocher + libellé ---
        carte.coche = new CheckBox(this);
        carte.coche.setText(g.libelle);
        carte.coche.setTextSize(17);
        carte.coche.setTypeface(carte.coche.getTypeface(), android.graphics.Typeface.BOLD);
        carte.confirmationRequise = origine.aUneErreur()
                || origine.confiance == ElementDetecte.Confiance.BASSE;
        // Coché d'office seulement si le contrôle automatique a réussi (ou groupe ajouté à la main).
        carte.coche.setChecked(origine.page == 0
                || (origine.confiance == ElementDetecte.Confiance.HAUTE && !origine.aUneErreur()));
        carte.coche.setOnCheckedChangeListener((bouton, cochee) -> {
            if (carte.ignorer || !cochee || !carte.confirmationRequise || carte.confirme) {
                return;
            }
            carte.ignorer = true;
            bouton.setChecked(false);
            carte.ignorer = false;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.scan_confirmer_titre)
                    .setMessage(getString(R.string.scan_confirmer_msg, origine.repere))
                    .setNegativeButton(R.string.scan_annuler, null)
                    .setPositiveButton(R.string.scan_confirmer_oui, (d, w) -> {
                        carte.confirme = true;
                        carte.ignorer = true;
                        bouton.setChecked(true);
                        carte.ignorer = false;
                    })
                    .show();
        });
        racine.addView(carte.coche, plein(0));

        // --- Fiabilité et origine ---
        if (origine.page > 0) {
            int libelleConfiance;
            int couleur;
            switch (origine.confiance) {
                case HAUTE:
                    libelleConfiance = R.string.scan_confiance_haute;
                    couleur = COULEUR_OK;
                    break;
                case MOYENNE:
                    libelleConfiance = R.string.scan_confiance_moyenne;
                    couleur = COULEUR_ATTENTION;
                    break;
                default:
                    libelleConfiance = R.string.scan_confiance_basse;
                    couleur = COULEUR_ERREUR;
                    break;
            }
            racine.addView(texte(getString(libelleConfiance) + " · "
                    + getString(R.string.scan_page_n, origine.page)
                    + (origine.dimensions.isEmpty() ? "" : " · " + origine.dimensions),
                    13, couleur, true), plein(2));

            if (premierDeLElement) {
                for (Alerte a : origine.alertes) {
                    int c = a.niveau == Alerte.Niveau.ERREUR ? COULEUR_ERREUR
                            : a.niveau == Alerte.Niveau.ATTENTION ? COULEUR_ATTENTION : COULEUR_GRIS;
                    String puce = a.niveau == Alerte.Niveau.ERREUR ? "✖ "
                            : a.niveau == Alerte.Niveau.ATTENTION ? "⚠ " : "ℹ ";
                    racine.addView(texte(puce + a.message, 13, c, false), plein(4));
                }
                if (!origine.source.isEmpty()) {
                    racine.addView(texte(getString(R.string.scan_lu_sur_plan, origine.source),
                            12, COULEUR_GRIS, false), plein(4));
                }
            }
        }

        // --- Type, nombre, longueur ---
        carte.choixType = new Spinner(this);
        List<String> libellesTypes = new ArrayList<>();
        for (TypeElementScan t : TYPES_CHOIX) {
            libellesTypes.add(t == TypeElementScan.INCONNU
                    ? getString(R.string.scan_type_choisir)
                    : TypeElement.depuisCode(t.getCode()).singulierMajuscule(this));
        }
        ArrayAdapter<String> adaptateurTypes = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, libellesTypes);
        carte.choixType.setAdapter(adaptateurTypes);
        for (int i = 0; i < TYPES_CHOIX.length; i++) {
            if (TYPES_CHOIX[i] == g.type) {
                carte.choixType.setSelection(i);
            }
        }
        racine.addView(carte.choixType, plein(8));

        LinearLayout champs = new LinearLayout(this);
        champs.setOrientation(LinearLayout.HORIZONTAL);
        carte.saisieNombre = champNumerique(R.string.scan_champ_nombre, false,
                g.nombre == null ? "" : String.valueOf(g.nombre));
        carte.saisieLongueur = champNumerique(R.string.scan_champ_longueur, true,
                g.longueurM == null ? "" : String.format(Locale.US, "%.2f", g.longueurM));
        champs.addView(carte.saisieNombre, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        champs.addView(carte.saisieLongueur, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        racine.addView(champs, plein(4));

        // --- Barres ---
        carte.layoutBarres = new LinearLayout(this);
        carte.layoutBarres.setOrientation(LinearLayout.VERTICAL);
        racine.addView(carte.layoutBarres, plein(4));
        for (BarreDetectee b : g.barres) {
            ajouterLigneBarre(carte, b.designation(), String.valueOf(b.quantite));
        }

        MaterialButton ajouterBarre = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        ajouterBarre.setText(R.string.scan_ajouter_barre);
        ajouterBarre.setAllCaps(false);
        ajouterBarre.setOnClickListener(v -> ajouterLigneBarre(carte, "N12", "1"));
        racine.addView(ajouterBarre, plein(0));

        carte.problemes = texte("", 13, COULEUR_ERREUR, true);
        carte.problemes.setVisibility(View.GONE);
        racine.addView(carte.problemes, plein(4));

        cartes.add(carte);
        zoneCartes.addView(racine, lpCarte);
    }

    private EditText champNumerique(int hint, boolean decimal, String valeur) {
        EditText champ = new EditText(this);
        champ.setHint(hint);
        champ.setInputType(decimal
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_NUMBER);
        champ.setText(valeur);
        return champ;
    }

    private void ajouterLigneBarre(CarteGroupe carte, String designation, String quantite) {
        LinearLayout ligne = new LinearLayout(this);
        ligne.setOrientation(LinearLayout.HORIZONTAL);
        ligne.setGravity(Gravity.CENTER_VERTICAL);

        List<String> choix = new ArrayList<>(typesBarres);
        if (!choix.contains(designation)) {
            // Diamètre absent de l'application : on le garde visible, l'import sera refusé.
            choix.add(designation);
        }
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, choix));
        spinner.setSelection(choix.indexOf(designation));
        ligne.addView(spinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        EditText champ = new EditText(this);
        champ.setHint(R.string.scan_champ_quantite);
        champ.setInputType(InputType.TYPE_CLASS_NUMBER);
        champ.setText(quantite);
        ligne.addView(champ, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView retirer = texte("✕", 20, COULEUR_ERREUR, true);
        retirer.setPadding(dp(14), dp(8), dp(14), dp(8));
        retirer.setContentDescription(getString(R.string.scan_retirer_barre));
        retirer.setOnClickListener(v -> carte.layoutBarres.removeView(ligne));
        ligne.addView(retirer);

        carte.layoutBarres.addView(ligne);
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    private void importer() {
        List<ControleImport.GroupeSaisi> valides = new ArrayList<>();
        int aCorriger = 0;
        CarteGroupe premiere = null;

        for (CarteGroupe carte : cartes) {
            if (!carte.coche.isChecked()) {
                continue;
            }
            ControleImport.GroupeSaisi saisi = carte.lire();
            List<ControleImport.Probleme> bloquants =
                    ControleImport.problemes(saisi, designationsAutorisees);
            carte.afficherProblemes(bloquants, ControleImport.avertissements(saisi));
            if (bloquants.isEmpty()) {
                valides.add(saisi);
            } else {
                aCorriger++;
                if (premiere == null) {
                    premiere = carte;
                }
            }
        }

        if (aCorriger > 0) {
            Toast.makeText(this, getString(R.string.scan_corriger, aCorriger), Toast.LENGTH_LONG).show();
            final CarteGroupe cible = premiere;
            defilement.post(() -> defilement.smoothScrollTo(0, zoneCartes.getTop() + cible.racine.getTop()));
            return;
        }
        if (valides.isEmpty()) {
            Toast.makeText(this, R.string.scan_aucun_groupe, Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_TYPE_ELEMENT, EntreesCalcul.typePrincipal(valides));
        intent.putExtra(MainActivity.EXTRA_ENTREES_SCAN, EntreesCalcul.versJson(valides));
        startActivity(intent);
        finish();
    }
}