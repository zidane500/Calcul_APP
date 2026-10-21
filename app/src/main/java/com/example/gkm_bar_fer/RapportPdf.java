package com.example.gkm_bar_fer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Génère le rapport PDF "professionnel" (en-tête, chiffres clés, besoins,
 * approvisionnement, plan de coupe en barres colorées, pied de page avec logo).
 *
 * La classe est indépendante de MainActivity : MainActivity remplit un objet
 * {@link Donnees} puis appelle {@link #generer(PdfDocument, Donnees)}.
 */
public final class RapportPdf {

    private RapportPdf() {
    }

    // ------------------------------------------------------------------
    // Données d'entrée
    // ------------------------------------------------------------------

    /** Une ligne du tableau "Besoins du chantier". */
    public static class Besoin {
        public int nombre;            // nombre d'éléments (ex : 5 poutres)
        public double longueur;       // longueur d'un élément en m
        public String typeFer = "";   // ex : N35
        public int coupesParElement;  // coupes par élément
        public int totalCoupes;       // nombre * coupesParElement
        public String typeSingulier = ""; // ex : dalle (vide = celui de Donnees)
        public String typePluriel = "";   // ex : dalles (vide = celui de Donnees)
    }

    /** Une barre utilisée (neuve ou issue du stock) avec ses coupes. */
    public static class BarreDetail {
        public double longueurTotale;                  // 12 m ou longueur du morceau de stock
        public List<Double> coupes = new ArrayList<>();
        public double chute;                           // reste après les coupes
        public boolean depuisStock;
        public String affectation = "";                // texte "2 coupes de ... pour ..."
    }

    /** Toutes les barres d'un même type de fer. */
    public static class GroupeBarres {
        public String typeFer = "";
        public int barresAcheter;
        // barres neuves à acheter
        public String quantiteStock = "";   // détail du stock prélevé (vide = stock non utilisé)
        public List<BarreDetail> barres = new ArrayList<>();
    }

    public static class Donnees {
        public String nomClient;                       // null ou vide = calcul libre
        public String typeSingulier = "";              // ex : poutre
        public String typePluriel = "";                // ex : poutres
        public String date = "";
        public double longueurBarreStandard = 12.0;
        public List<Besoin> besoins = new ArrayList<>();
        public List<GroupeBarres> groupes = new ArrayList<>();
        public Bitmap logo;                            // peut être null
        public String nomApp = "Fer Pro";
    }

    // ------------------------------------------------------------------
    // API publique
    // ------------------------------------------------------------------

    /**
     * Charge le logo de l'application (badge du lanceur) et le découpe en rond.
     * Retourne null en cas d'échec : le rapport est alors généré sans logo.
     */
    public static Bitmap chargerLogo(Context context) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap source = BitmapFactory.decodeResource(
                    context.getResources(), R.mipmap.ic_launcher_foreground, options);
            if (source == null) {
                return null;
            }

            int taille = Math.min(source.getWidth(), source.getHeight());
            Bitmap rond = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888);
            Canvas toile = new Canvas(rond);
            Paint peinture = new Paint(Paint.ANTI_ALIAS_FLAG);
            peinture.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            toile.drawCircle(taille / 2f, taille / 2f, taille / 2f, peinture);
            return rond;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Dessine tout le rapport dans le document. Deux passes : la première compte les pages
     * (pour afficher "1 / 3"), la seconde dessine réellement.
     */
    public static void generer(PdfDocument document, Donnees donnees) {
        PdfDocument essai = new PdfDocument();
        int totalPages = new Rendu(essai, donnees, 0).dessiner();
        essai.close();

        new Rendu(document, donnees, totalPages).dessiner();
    }

    // ------------------------------------------------------------------
    // Utilitaires de formatage
    // ------------------------------------------------------------------

    private static String fmt(double valeur) {
        return String.format(Locale.US, "%.2f", valeur).replace('.', ',');
    }

    private static String fmtCourt(double valeur) {
        if (Math.abs(valeur - Math.rint(valeur)) < 1e-9) {
            return String.valueOf((long) Math.rint(valeur));
        }
        return fmt(valeur);
    }

    private static String majuscule(String texte) {
        if (texte == null || texte.isEmpty()) {
            return "";
        }
        return texte.substring(0, 1).toUpperCase(Locale.FRANCE) + texte.substring(1);
    }

    private static List<String> couper(String texte, Paint paint, float largeur) {
        List<String> lignes = new ArrayList<>();
        String courant = "";
        for (String mot : texte.split(" ")) {
            String essai = courant.isEmpty() ? mot : courant + " " + mot;
            if (courant.isEmpty() || paint.measureText(essai) <= largeur) {
                courant = essai;
            } else {
                lignes.add(courant);
                courant = mot;
            }
        }
        if (!courant.isEmpty()) {
            lignes.add(courant);
        }
        return lignes;
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    private static final class Rendu {

        // Page A4 en points
        private static final int LARGEUR = 595;
        private static final int HAUTEUR = 842;
        private static final int MARGE = 40;
        private static final float LARGEUR_UTILE = LARGEUR - 2f * MARGE;
        private static final float HAUT_PIED = HAUTEUR - 86f;     // début du pied de page
        private static final float LIMITE_CONTENU = HAUT_PIED - 12f;

        // Couleurs
        private static final int MARINE = 0xFF14304D;
        private static final int BLEU = 0xFF1B75BC;
        private static final int VERT = 0xFF187A6E;
        private static final int GRIS_TEXTE = 0xFF5F6F82;
        private static final int GRIS_LIGNE = 0xFFD9E0E8;
        private static final int FOND_CLAIR = 0xFFEEF3F8;
        private static final int FOND_CHUTE = 0xFFE3E8F0;
        private static final int BLANC = 0xFFFFFFFF;

        private final PdfDocument document;
        private final Donnees d;
        private final int totalPages;

        private PdfDocument.Page page;
        private Canvas canvas;
        private float y;
        private int numeroPage = 0;

        private final Paint paintLogo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        Rendu(PdfDocument document, Donnees donnees, int totalPages) {
            this.document = document;
            this.d = donnees;
            this.totalPages = totalPages;
        }

        // ---------------- Peintures ----------------

        private Paint texte(float taille, int couleur, boolean gras) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextSize(taille);
            p.setColor(couleur);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, gras ? Typeface.BOLD : Typeface.NORMAL));
            return p;
        }

        private Paint remplissage(int couleur) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(couleur);
            p.setStyle(Paint.Style.FILL);
            return p;
        }

        private Paint trait(int couleur, float epaisseur) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(couleur);
            p.setStrokeWidth(epaisseur);
            return p;
        }

        // ---------------- Pages ----------------

        private void nouvellePage() {
            if (page != null) {
                piedDePage();
                document.finishPage(page);
            }
            numeroPage++;
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(LARGEUR, HAUTEUR, numeroPage).create();
            page = document.startPage(info);
            canvas = page.getCanvas();

            // Bandeau bleu en haut
            canvas.drawRect(0, 0, LARGEUR, 8, remplissage(BLEU));
            y = 44;

            // Pages suivantes : petit rappel du titre
            if (numeroPage > 1) {
                canvas.drawText(titreRapport(), MARGE, 36, texte(8.5f, GRIS_TEXTE, true));
                y = 54;
            }
        }

        private void assurerEspace(float hauteur) {
            if (y + hauteur > LIMITE_CONTENU) {
                nouvellePage();
            }
        }

        private void piedDePage() {
            canvas.drawLine(MARGE, HAUT_PIED, LARGEUR - MARGE, HAUT_PIED, trait(GRIS_LIGNE, 0.8f));

            float tailleLogo = 30f;
            float hautLogo = HAUT_PIED + 9f;
            float centre = LARGEUR / 2f;

            if (d.logo != null) {
                canvas.drawBitmap(d.logo, null,
                        new RectF(centre - tailleLogo / 2f, hautLogo,
                                centre + tailleLogo / 2f, hautLogo + tailleLogo),
                        paintLogo);
            }

            Paint paintNom = texte(10f, MARINE, true);
            float largeurNom = paintNom.measureText(d.nomApp);
            canvas.drawText(d.nomApp, centre - largeurNom / 2f, hautLogo + tailleLogo + 13f, paintNom);

            Paint paintPetit = texte(8f, GRIS_TEXTE, false);
            float ligneBase = hautLogo + tailleLogo / 2f + 3f;
            canvas.drawText("Rapport de calcul | " + majuscule(d.typePluriel), MARGE, ligneBase, paintPetit);

            String numero = numeroPage + " / " + Math.max(totalPages, numeroPage);
            canvas.drawText(numero, LARGEUR - MARGE - paintPetit.measureText(numero), ligneBase, paintPetit);
        }

        // ---------------- Contenu ----------------

        int dessiner() {
            nouvellePage();
            enTete();
            chiffresCles();
            sectionBesoins();
            sectionApprovisionnement();
            sectionPlanDeCoupe();
            synthese();

            piedDePage();
            document.finishPage(page);
            return numeroPage;
        }

        private String titreRapport() {
            boolean client = d.nomClient != null && !d.nomClient.trim().isEmpty();
            return client ? "Résultat de calcul de " + d.nomClient.trim() : "Résultat de calcul";
        }

        private void enTete() {
            // Titre (réduit / sur 2 lignes si le nom du client est long)
            Paint paintTitre = texte(25f, MARINE, true);
            List<String> lignes = couper(titreRapport(), paintTitre, LARGEUR_UTILE);
            if (lignes.size() > 2) {
                paintTitre = texte(18f, MARINE, true);
                lignes = couper(titreRapport(), paintTitre, LARGEUR_UTILE);
            }
            float interligne = paintTitre.getTextSize() + 5f;
            y += paintTitre.getTextSize();
            for (String ligne : lignes) {
                canvas.drawText(ligne, MARGE, y, paintTitre);
                y += interligne;
            }
            y -= interligne - 6f;

            // Type de calcul
            y += 16f;
            canvas.drawText("Approvisionnement et plan de coupe | " + majuscule(d.typePluriel),
                    MARGE, y, texte(11f, GRIS_TEXTE, false));

            // Séparateur
            y += 16f;
            canvas.drawLine(MARGE, y, LARGEUR - MARGE, y, trait(GRIS_LIGNE, 0.8f));

            // Date + périmètre
            y += 18f;
            float colonne2 = MARGE + 265f;
            Paint paintLabel = texte(7.5f, GRIS_TEXTE, true);
            Paint paintValeur = texte(10f, MARINE, false);

            canvas.drawText("DATE DU CALCUL", MARGE, y, paintLabel);
            canvas.drawText("PÉRIMÈTRE", colonne2, y, paintLabel);

            float yValeur = y + 16f;
            canvas.drawText(d.date, MARGE, yValeur, paintValeur);
            List<String> perimetre = couper(textePerimetre(), paintValeur, LARGEUR - MARGE - colonne2);
            float yFin = yValeur;
            for (String ligne : perimetre) {
                canvas.drawText(ligne, colonne2, yFin, paintValeur);
                yFin += 13f;
            }
            y = Math.max(yValeur, yFin - 13f) + 14f;
            canvas.drawLine(MARGE, y, LARGEUR - MARGE, y, trait(GRIS_LIGNE, 0.8f));
            y += 16f;
        }

        private String textePerimetre() {
            LinkedHashMap<String, String> elements = new LinkedHashMap<>();
            List<String> fers = new ArrayList<>();
            for (Besoin b : d.besoins) {
                String cle = b.nombre + "|" + b.longueur + "|" + b.typeSingulier;
                if (!elements.containsKey(cle)) {
                    String pluriel = b.typePluriel.isEmpty() ? d.typePluriel : b.typePluriel;
                    String singulier = b.typeSingulier.isEmpty() ? d.typeSingulier : b.typeSingulier;
                    elements.put(cle, b.nombre + " " + (b.nombre > 1 ? pluriel : singulier)
                            + " de " + fmt(b.longueur) + " m");
                }
                if (!fers.contains(b.typeFer)) {
                    fers.add(b.typeFer);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String e : elements.values()) {
                if (sb.length() > 0) {
                    sb.append(" ; ");
                }
                sb.append(e);
            }
            if (!fers.isEmpty()) {
                sb.append(" | Fer ");
                for (int i = 0; i < fers.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(fers.get(i));
                }
            }
            return sb.toString();
        }

        private void chiffresCles() {
            int barresAcheter = 0;
            int coupes = 0;
            double chutes = 0;
            for (GroupeBarres g : d.groupes) {
                barresAcheter += g.barresAcheter;
                for (BarreDetail b : g.barres) {
                    coupes += b.coupes.size();
                    if (b.chute > 1e-9) {
                        chutes += b.chute;
                    }
                }
            }

            String[] valeurs = {String.valueOf(barresAcheter), String.valueOf(coupes), fmt(chutes) + " m"};
            String[] libelles = {"BARRES À ACHETER", "COUPES À RÉALISER", "CHUTES TOTALES"};

            float ecart = 12f;
            float largeurCase = (LARGEUR_UTILE - 2f * ecart) / 3f;
            float hauteurCase = 56f;

            Paint paintValeur = texte(21f, BLEU, true);
            Paint paintLibelle = texte(7.5f, GRIS_TEXTE, true);

            for (int i = 0; i < 3; i++) {
                float x = MARGE + i * (largeurCase + ecart);
                canvas.drawRect(x, y, x + largeurCase, y + hauteurCase, remplissage(FOND_CLAIR));
                canvas.drawText(valeurs[i], x + 14f, y + 28f, paintValeur);
                canvas.drawText(libelles[i], x + 14f, y + 46f, paintLibelle);
            }
            y += hauteurCase + 26f;
        }

        private void titreSection(String numero, String titre) {
            assurerEspace(70f);
            canvas.drawText(numero, MARGE, y, texte(9f, BLEU, true));
            canvas.drawText(titre, MARGE + 26f, y + 1f, texte(13.5f, MARINE, true));
            y += 18f;
        }

        // ---- 01 Besoins ----

        private void sectionBesoins() {
            titreSection("01", "Besoins du chantier");

            float[] colonnes = {10f, 122f, 194f, 281f, 386f};
            String[] entetes = {"Élément", "Quantité", "Longueur", "Type de fer", "Coupes / élément"};

            canvas.drawRect(MARGE, y, LARGEUR - MARGE, y + 22f, remplissage(MARINE));
            Paint paintEntete = texte(7.5f, BLANC, true);
            for (int i = 0; i < entetes.length; i++) {
                canvas.drawText(entetes[i], MARGE + colonnes[i], y + 14f, paintEntete);
            }
            y += 22f;

            Paint paintCellule = texte(9.5f, MARINE, false);
            for (Besoin b : d.besoins) {
                assurerEspace(26f);
                String[] cellules = {
                        majuscule(b.typeSingulier.isEmpty() ? d.typeSingulier : b.typeSingulier),
                        String.valueOf(b.nombre),
                        fmt(b.longueur) + " m",
                        b.typeFer,
                        String.valueOf(b.coupesParElement)
                };
                for (int i = 0; i < cellules.length; i++) {
                    canvas.drawText(cellules[i], MARGE + colonnes[i], y + 16f, paintCellule);
                }
                y += 24f;
                canvas.drawLine(MARGE, y, LARGEUR - MARGE, y, trait(GRIS_LIGNE, 0.8f));
            }

            // Besoin total par type de fer
            LinkedHashMap<String, double[]> totaux = new LinkedHashMap<>();   // {coupes, longueur totale}
            LinkedHashMap<String, Double> longueurCommune = new LinkedHashMap<>();
            for (Besoin b : d.besoins) {
                double[] t = totaux.get(b.typeFer);
                if (t == null) {
                    t = new double[]{0, 0};
                    totaux.put(b.typeFer, t);
                    longueurCommune.put(b.typeFer, b.longueur);
                } else if (Math.abs(longueurCommune.get(b.typeFer) - b.longueur) > 1e-9) {
                    longueurCommune.put(b.typeFer, Double.NaN);
                }
                t[0] += b.totalCoupes;
                t[1] += b.totalCoupes * b.longueur;
            }

            StringBuilder sb = new StringBuilder("Besoin total : ");
            boolean premier = true;
            for (Map.Entry<String, double[]> e : totaux.entrySet()) {
                if (!premier) {
                    sb.append(" ; ");
                }
                premier = false;
                int nb = (int) Math.rint(e.getValue()[0]);
                sb.append(nb).append(nb > 1 ? " coupes" : " coupe");
                double commune = longueurCommune.get(e.getKey());
                if (!Double.isNaN(commune)) {
                    sb.append(" de ").append(fmt(commune)).append(" m");
                }
                sb.append(", soit ").append(fmt(e.getValue()[1])).append(" m de fer ").append(e.getKey());
            }
            sb.append('.');

            Paint paintTotal = texte(9.5f, MARINE, true);
            y += 8f;
            for (String ligne : couper(sb.toString(), paintTotal, LARGEUR_UTILE - 20f)) {
                assurerEspace(16f);
                y += 12f;
                canvas.drawText(ligne, MARGE + 10f, y, paintTotal);
                y += 2f;
            }
            y += 22f;
        }

        // ---- 02 Approvisionnement ----

        private void sectionApprovisionnement() {
            titreSection("02", "Approvisionnement");
            y += 2f;

            Paint paintFer = texte(12.5f, BLEU, true);
            Paint paintTitreLigne = texte(10.5f, MARINE, true);
            Paint paintDetail = texte(8f, GRIS_TEXTE, false);

            for (GroupeBarres g : d.groupes) {
                double achetee = g.barresAcheter * d.longueurBarreStandard;
                double affectee = 0;
                for (BarreDetail b : g.barres) {
                    if (!b.depuisStock) {
                        for (Double c : b.coupes) {
                            affectee += c;
                        }
                    }
                }

                List<String> lignesStock = g.quantiteStock.isEmpty()
                        ? Collections.<String>emptyList()
                        : couper("Quantité prélevée sur le stock : " + g.quantiteStock,
                        paintDetail, LARGEUR - 2f * MARGE - 95f - 12f);

                float hauteur = 48f + 14f * lignesStock.size();
                assurerEspace(hauteur + 8f);

                canvas.drawRect(MARGE, y, LARGEUR - MARGE, y + hauteur, remplissage(FOND_CLAIR));
                canvas.drawText(g.typeFer, MARGE + 12f, y + 28f, paintFer);

                String titre = g.barresAcheter > 0
                        ? g.barresAcheter + (g.barresAcheter > 1 ? " barres" : " barre")
                          + " standard de " + fmt(d.longueurBarreStandard) + " m"
                        : "Aucune barre à acheter";
                canvas.drawText(titre, MARGE + 95f, y + 21f, paintTitreLigne);

                canvas.drawText("Longueur achetée : " + fmt(achetee) + " m  |  Longueur affectée aux coupes : "
                        + fmt(affectee) + " m", MARGE + 95f, y + 36f, paintDetail);

                for (int i = 0; i < lignesStock.size(); i++) {
                    canvas.drawText(lignesStock.get(i), MARGE + 95f, y + 50f + 14f * i, paintDetail);
                }
                y += hauteur + 8f;
            }
            y += 14f;
        }

        // ---- 03 Plan de coupe ----

        private static final class LigneBarre {
            BarreDetail barre;
            int nombre;
            int premierNumero;
        }

        private String signature(BarreDetail b) {
            StringBuilder sb = new StringBuilder();
            sb.append(b.depuisStock).append('|').append(fmt(b.longueurTotale));
            for (Double c : b.coupes) {
                sb.append('|').append(fmt(c));
            }
            sb.append('|').append(fmt(b.chute)).append('|').append(b.affectation);
            return sb.toString();
        }

        private void sectionPlanDeCoupe() {
            titreSection("03", "Plan de coupe détaillé");
            y += 4f;

            int numero = 1;
            Paint paintEtiquette = texte(8.5f, MARINE, true);
            Paint paintDroite = texte(8.5f, GRIS_TEXTE, false);
            Paint paintAffectation = texte(8f, GRIS_TEXTE, false);

            for (GroupeBarres g : d.groupes) {
                // Regroupe les barres strictement identiques pour garder un rapport lisible.
                LinkedHashMap<String, LigneBarre> lignes = new LinkedHashMap<>();
                for (BarreDetail b : g.barres) {
                    String cle = signature(b);
                    LigneBarre l = lignes.get(cle);
                    if (l == null) {
                        l = new LigneBarre();
                        l.barre = b;
                        lignes.put(cle, l);
                    }
                    l.nombre++;
                }

                // 1 ou 2 barres identiques : détaillées une par une ; 3 et plus : regroupées.
                List<LigneBarre> aAfficher = new ArrayList<>();
                for (LigneBarre l : lignes.values()) {
                    if (l.nombre <= 2) {
                        for (int k = 0; k < l.nombre; k++) {
                            LigneBarre seule = new LigneBarre();
                            seule.barre = l.barre;
                            seule.nombre = 1;
                            aAfficher.add(seule);
                        }
                    } else {
                        aAfficher.add(l);
                    }
                }

                for (LigneBarre l : aAfficher) {
                    l.premierNumero = numero;
                    numero += l.nombre;

                    String prefixe = l.nombre > 1 ? "Affectation pour chaque barre : " : "Affectation : ";
                    List<String> texteAffectation =
                            couper(prefixe + l.barre.affectation + ".", paintAffectation, LARGEUR_UTILE);

                    float hauteurBloc = 14f + 24f + 6f + 11f * texteAffectation.size() + 14f;
                    assurerEspace(hauteurBloc);

                    // Étiquette à gauche, type de fer + longueur à droite
                    String etiquette;
                    if (l.nombre == 1) {
                        etiquette = String.format(Locale.FRANCE, "BARRE %02d", l.premierNumero);
                    } else {
                        etiquette = String.format(Locale.FRANCE, "BARRES %02d À %02d  (×%d)",
                                l.premierNumero, l.premierNumero + l.nombre - 1, l.nombre);
                    }
                    canvas.drawText(etiquette, MARGE, y + 8f, paintEtiquette);

                    String droite = g.typeFer + " | "
                            + (l.barre.depuisStock ? "stock " : "")
                            + fmt(l.barre.longueurTotale) + " m";
                    canvas.drawText(droite, LARGEUR - MARGE - paintDroite.measureText(droite), y + 8f, paintDroite);

                    dessinerBarre(l.barre, y + 14f);
                    y += 14f + 24f + 2f;

                    for (String ligne : texteAffectation) {
                        y += 11f;
                        canvas.drawText(ligne, MARGE, y, paintAffectation);
                    }
                    y += 14f;
                }
            }
        }

        private void dessinerBarre(BarreDetail b, float haut) {
            float hauteur = 24f;
            double somme = 0;
            for (Double c : b.coupes) {
                somme += c;
            }
            double total = Math.max(b.longueurTotale, somme + Math.max(0, b.chute));
            if (total <= 0) {
                return;
            }

            float echelle = (float) (LARGEUR_UTILE / total);
            Paint paintEtiquette = texte(8.5f, BLANC, true);
            Paint paintChute = texte(8.5f, GRIS_TEXTE, true);
            float x = MARGE;

            for (int i = 0; i < b.coupes.size(); i++) {
                float largeur = (float) (b.coupes.get(i) * echelle);
                int couleur = (i % 2 == 0) ? BLEU : VERT;
                canvas.drawRect(x, haut, x + largeur, haut + hauteur, remplissage(couleur));

                String long1 = "Coupe " + (i + 1) + " : " + fmt(b.coupes.get(i)) + " m";
                String long2 = fmt(b.coupes.get(i)) + " m";
                String choisi = null;
                if (paintEtiquette.measureText(long1) <= largeur - 10f) {
                    choisi = long1;
                } else if (paintEtiquette.measureText(long2) <= largeur - 8f) {
                    choisi = long2;
                }
                if (choisi != null) {
                    canvas.drawText(choisi, x + 7f, haut + 15f, paintEtiquette);
                }

                // petit espace blanc entre deux coupes
                canvas.drawRect(x + largeur - 0.8f, haut, x + largeur, haut + hauteur, remplissage(BLANC));
                x += largeur;
            }

            if (b.chute > 1e-9) {
                float largeur = (float) (b.chute * echelle);
                canvas.drawRect(x, haut, x + largeur, haut + hauteur, remplissage(FOND_CHUTE));
                String texteChute = fmt(b.chute) + " m";
                if (paintChute.measureText(texteChute) <= largeur - 8f) {
                    canvas.drawText(texteChute, x + 7f, haut + 15f, paintChute);
                }
            }
        }

        // ---- Synthèse ----

        private void synthese() {
            double totalBarres = 0;
            double totalCoupes = 0;
            boolean stockUtilise = false;
            TreeMap<Long, Integer> chutes = new TreeMap<>(Collections.reverseOrder());   // centimètres -> nombre

            for (GroupeBarres g : d.groupes) {
                for (BarreDetail b : g.barres) {
                    totalBarres += b.longueurTotale;
                    stockUtilise |= b.depuisStock;
                    for (Double c : b.coupes) {
                        totalCoupes += c;
                    }
                    if (b.chute > 1e-9) {
                        long cm = Math.round(b.chute * 100.0);
                        Integer n = chutes.get(cm);
                        chutes.put(cm, n == null ? 1 : n + 1);
                    }
                }
            }

            double utilisation = totalBarres > 0 ? 100.0 * totalCoupes / totalBarres : 0;

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Long, Integer> e : chutes.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e.getValue()).append(e.getValue() > 1 ? " morceaux" : " morceau")
                        .append(" de ").append(fmt(e.getKey() / 100.0)).append(" m");
            }
            String texteChutes = chutes.isEmpty() ? "Aucune chute" : sb.toString();

            Paint paintLabel = texte(7.5f, GRIS_TEXTE, true);
            Paint paintGrand = texte(21f, VERT, true);
            Paint paintChutes = texte(10.5f, MARINE, true);
            Paint paintNote = texte(7.5f, GRIS_TEXTE, false);

            float colonne2 = MARGE + 150f;
            List<String> lignesChutes = couper(texteChutes, paintChutes, LARGEUR - MARGE - colonne2 - 8f);
            float hauteur = 62f + 13f * Math.max(0, lignesChutes.size() - 1) + 22f;
            assurerEspace(hauteur);

            y += 4f;
            canvas.drawLine(MARGE, y, LARGEUR - MARGE, y, trait(GRIS_LIGNE, 0.8f));
            y += 18f;

            canvas.drawText("UTILISATION MATIÈRE", MARGE, y, paintLabel);
            canvas.drawText("CHUTES RÉCUPÉRABLES", colonne2, y, paintLabel);

            canvas.drawText(String.format(Locale.US, "%.1f", utilisation).replace('.', ',') + " %",
                    MARGE, y + 24f, paintGrand);

            float yChutes = y + 20f;
            for (String ligne : lignesChutes) {
                canvas.drawText(ligne, colonne2, yChutes, paintChutes);
                yChutes += 13f;
            }

            y = Math.max(y + 24f, yChutes - 13f) + 20f;
            String note = "Base : barres de " + fmtCourt(d.longueurBarreStandard) + " m prévues par l'application"
                    + (stockUtilise ? " et morceaux du stock utilisés" : "")
                    + " ; longueur de trait de coupe non déduite.";
            for (String ligne : couper(note, paintNote, LARGEUR_UTILE)) {
                canvas.drawText(ligne, MARGE, y, paintNote);
                y += 10f;
            }
        }
    }
}