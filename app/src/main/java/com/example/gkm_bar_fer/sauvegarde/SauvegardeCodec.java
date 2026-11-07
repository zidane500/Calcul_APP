package com.example.gkm_bar_fer.sauvegarde;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Format du fichier de sauvegarde Fer Pro (.ferpro) : écriture, lecture et vérification.
 *
 * Un fichier .ferpro est un ZIP qui contient exactement deux entrées :
 *  - manifest.json : format, version du format, version de l'app, date, somme de contrôle SHA-256 ;
 *  - donnees.json  : le contenu des fichiers de préférences (clients, calculs, stocks).
 *
 * Ce code ne touche à aucun fichier et n'utilise aucune classe Android : il travaille sur des
 * Map et des octets, donc il se teste entièrement sur PC. La lecture est volontairement stricte :
 * un fichier abîmé, modifié, trop gros ou contenant autre chose que des données métier est refusé
 * AVANT que quoi que ce soit ne soit écrit dans l'application.
 */
public final class SauvegardeCodec {

    private SauvegardeCodec() {
        // Classe utilitaire : pas d'instance.
    }

    public static final String EXTENSION = ".ferpro";

    /** Version du format écrite par cette version de l'app (et lue au maximum). */
    public static final int VERSION_FORMAT = 1;

    static final String FORMAT = "FERPRO_SAUVEGARDE";
    static final String ENTREE_MANIFEST = "manifest.json";
    static final String ENTREE_DONNEES = "donnees.json";

    /** Taille maximale d'un fichier .ferpro lu (compressé). */
    static final long MAX_OCTETS_FICHIER = 25L * 1024 * 1024;

    /** Taille maximale d'une entrée une fois décompressée (protège contre les "bombes ZIP"). */
    static final long MAX_OCTETS_ENTREE = 40L * 1024 * 1024;

    private static final int MAX_FICHIERS_PREFS = 5000;
    private static final int MAX_LONGUEUR_CLE = 200;

    // Types de valeurs (étiquette "t" dans donnees.json)
    private static final String T_TEXTE = "s";
    private static final String T_LONG = "l";
    private static final String T_ENTIER = "i";
    private static final String T_BOOLEEN = "b";
    private static final String T_DECIMAL = "f";
    private static final String T_ENSEMBLE = "ss";

    // ------------------------------------------------------------------
    // Résultats et erreurs
    // ------------------------------------------------------------------

    /** Pourquoi un fichier a été refusé (sert à choisir le message affiché au client). */
    public enum Motif {
        /** Ce n'est pas un fichier de sauvegarde lisible (vide, abîmé, pas un ZIP...). */
        FICHIER_ILLISIBLE,
        /** C'est un fichier lisible mais pas une sauvegarde Fer Pro. */
        FORMAT_INCONNU,
        /** Créé par une version plus récente de l'app : il faut mettre l'app à jour. */
        VERSION_TROP_RECENTE,
        /** Dépasse les tailles autorisées. */
        FICHIER_TROP_GRAND,
        /** Le contenu ne correspond pas à la somme de contrôle : fichier abîmé ou modifié. */
        SOMME_DE_CONTROLE,
        /** Contient des éléments interdits (ex : licence, entrée inattendue). */
        CONTENU_INTERDIT,
        /** Contient des données mal formées. */
        CONTENU_INVALIDE
    }

    public static final class InvalideException extends Exception {
        private static final long serialVersionUID = 1L;

        public final Motif motif;

        InvalideException(Motif motif, String detail) {
            super(motif + " : " + detail);
            this.motif = motif;
        }
    }

    /** Résumé affiché au client avant d'importer. Les compteurs sont recalculés depuis les données. */
    public static final class Apercu {
        public final int versionFormat;
        public final String versionApp;
        public final long dateMs;
        public final int nbClients;
        public final int nbCalculs;
        public final int nbTypesStock;
        public final int nbStocksClients;

        Apercu(int versionFormat, String versionApp, long dateMs, int nbClients, int nbCalculs,
               int nbTypesStock, int nbStocksClients) {
            this.versionFormat = versionFormat;
            this.versionApp = versionApp;
            this.dateMs = dateMs;
            this.nbClients = nbClients;
            this.nbCalculs = nbCalculs;
            this.nbTypesStock = nbTypesStock;
            this.nbStocksClients = nbStocksClients;
        }
    }

    /** Contenu d'une sauvegarde vérifiée : nom du fichier de prefs -> (clé -> valeur). */
    public static final class Contenu {
        public final Map<String, Map<String, Object>> prefs;
        public final Apercu apercu;

        Contenu(Map<String, Map<String, Object>> prefs, Apercu apercu) {
            this.prefs = prefs;
            this.apercu = apercu;
        }
    }

    // ------------------------------------------------------------------
    // Écriture
    // ------------------------------------------------------------------

    /**
     * Construit le contenu d'un fichier .ferpro.
     *
     * @param prefs      nom du fichier de préférences -> (clé -> valeur), tel que renvoyé par
     *                   SharedPreferences.getAll()
     * @param versionApp version de l'application (informatif)
     * @param dateMs     date de la sauvegarde
     * @throws IllegalArgumentException si un fichier non autorisé (ex : la licence) ou une valeur
     *                                  de type non géré est fourni : c'est une erreur de programmation,
     *                                  jamais une donnée du client.
     */
    public static byte[] ecrire(Map<String, ? extends Map<String, ?>> prefs,
                                String versionApp, long dateMs) {
        Map<String, Map<String, Object>> copie = copierEtVerifier(prefs);

        try {
            byte[] donnees = encoderDonnees(copie);

            String version = versionApp == null ? "" : versionApp;
            Apercu apercu = calculerApercu(copie, VERSION_FORMAT, version, dateMs);

            JSONObject manifest = new JSONObject();
            manifest.put("format", FORMAT);
            manifest.put("formatVersion", VERSION_FORMAT);
            manifest.put("versionApp", version);
            manifest.put("dateMs", dateMs);
            manifest.put("sha256", sha256Hex(donnees));
            // Informatifs (lisibles par un humain) : l'import les recalcule lui-même.
            manifest.put("nbClients", apercu.nbClients);
            manifest.put("nbCalculs", apercu.nbCalculs);
            manifest.put("nbTypesStock", apercu.nbTypesStock);
            manifest.put("nbStocksClients", apercu.nbStocksClients);

            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(sortie)) {
                ecrireEntree(zip, ENTREE_MANIFEST,
                        manifest.toString().getBytes(StandardCharsets.UTF_8), dateMs);
                ecrireEntree(zip, ENTREE_DONNEES, donnees, dateMs);
            }
            return sortie.toByteArray();
        } catch (JSONException | IOException e) {
            // Ne peut pas arriver avec des données en mémoire : on le signale clairement.
            throw new IllegalStateException("Création de la sauvegarde impossible", e);
        }
    }

    /**
     * Empreinte (SHA-256) du CONTENU des données : identique si les données sont identiques,
     * quel que soit l'ordre des clés, et différente dès qu'une valeur change.
     * Sert à ne pas recréer une sauvegarde quand rien n'a changé.
     */
    public static String empreinte(Map<String, ? extends Map<String, ?>> prefs) {
        try {
            return sha256Hex(encoderDonnees(copierEtVerifier(prefs)));
        } catch (JSONException e) {
            throw new IllegalStateException("Calcul de l'empreinte impossible", e);
        }
    }

    /** Contenu de donnees.json, toujours dans le même ordre (clés triées) : résultat déterministe. */
    private static byte[] encoderDonnees(Map<String, Map<String, Object>> copie)
            throws JSONException {
        JSONObject jsonPrefs = new JSONObject();
        for (Map.Entry<String, Map<String, Object>> fichier : copie.entrySet()) {
            JSONObject jsonCles = new JSONObject();
            for (Map.Entry<String, Object> cle : fichier.getValue().entrySet()) {
                jsonCles.put(cle.getKey(), encoderValeur(cle.getValue()));
            }
            jsonPrefs.put(fichier.getKey(), jsonCles);
        }
        JSONObject racine = new JSONObject();
        racine.put("prefs", jsonPrefs);
        return racine.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void ecrireEntree(ZipOutputStream zip, String nom, byte[] octets, long dateMs)
            throws IOException {
        ZipEntry entree = new ZipEntry(nom);
        entree.setTime(dateMs);
        zip.putNextEntry(entree);
        zip.write(octets);
        zip.closeEntry();
    }

    private static Map<String, Map<String, Object>> copierEtVerifier(
            Map<String, ? extends Map<String, ?>> prefs) {
        Map<String, Map<String, Object>> copie = new TreeMap<>();
        for (Map.Entry<String, ? extends Map<String, ?>> fichier : prefs.entrySet()) {
            String nom = fichier.getKey();
            if (!NomsPrefs.estAutorise(nom)) {
                throw new IllegalArgumentException(
                        "Fichier de préférences non sauvegardable : " + nom);
            }
            Map<String, Object> cles = new TreeMap<>();
            for (Map.Entry<String, ?> cle : fichier.getValue().entrySet()) {
                Object valeur = cle.getValue();
                if (valeur == null) {
                    continue;
                }
                if (!typeGere(valeur)) {
                    throw new IllegalArgumentException("Type de valeur non géré : "
                            + valeur.getClass().getName() + " (clé " + cle.getKey() + ")");
                }
                cles.put(cle.getKey(), valeur instanceof Set ? copierEnsemble(valeur) : valeur);
            }
            copie.put(nom, cles);
        }
        return copie;
    }

    private static boolean typeGere(Object v) {
        if (v instanceof String || v instanceof Long || v instanceof Integer
                || v instanceof Boolean || v instanceof Float) {
            return true;
        }
        if (v instanceof Set) {
            for (Object element : (Set<?>) v) {
                if (!(element instanceof String)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static Set<String> copierEnsemble(Object ensemble) {
        Set<String> copie = new TreeSet<>();
        for (Object element : (Set<?>) ensemble) {
            copie.add((String) element);
        }
        return copie;
    }

    private static JSONObject encoderValeur(Object valeur) throws JSONException {
        JSONObject objet = new JSONObject();
        if (valeur instanceof String) {
            objet.put("t", T_TEXTE).put("v", valeur);
        } else if (valeur instanceof Long) {
            objet.put("t", T_LONG).put("v", ((Long) valeur).longValue());
        } else if (valeur instanceof Integer) {
            objet.put("t", T_ENTIER).put("v", ((Integer) valeur).intValue());
        } else if (valeur instanceof Boolean) {
            objet.put("t", T_BOOLEEN).put("v", ((Boolean) valeur).booleanValue());
        } else if (valeur instanceof Float) {
            objet.put("t", T_DECIMAL).put("v", ((Float) valeur).doubleValue());
        } else {
            JSONArray tableau = new JSONArray();
            for (Object element : (Set<?>) valeur) {
                tableau.put(element);
            }
            objet.put("t", T_ENSEMBLE).put("v", tableau);
        }
        return objet;
    }

    // ------------------------------------------------------------------
    // Lecture et vérification
    // ------------------------------------------------------------------

    /** Lit un fichier .ferpro depuis un flux (ex : le fichier choisi par le client). */
    public static Contenu lire(InputStream flux) throws InvalideException {
        byte[] brut;
        try {
            brut = lireBorne(flux, MAX_OCTETS_FICHIER);
        } catch (IOException e) {
            throw new InvalideException(Motif.FICHIER_ILLISIBLE, "lecture impossible");
        }
        return lire(brut);
    }

    /** Lit et vérifie un fichier .ferpro. Ne modifie rien : renvoie le contenu ou lève une erreur. */
    public static Contenu lire(byte[] brut) throws InvalideException {
        if (brut == null || brut.length == 0) {
            throw new InvalideException(Motif.FICHIER_ILLISIBLE, "fichier vide");
        }
        if (brut.length > MAX_OCTETS_FICHIER) {
            throw new InvalideException(Motif.FICHIER_TROP_GRAND, "fichier trop gros");
        }

        byte[] octetsManifest = null;
        byte[] octetsDonnees = null;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(brut))) {
            ZipEntry entree;
            while ((entree = zip.getNextEntry()) != null) {
                String nom = entree.getName();
                boolean estManifest = ENTREE_MANIFEST.equals(nom);
                boolean estDonnees = ENTREE_DONNEES.equals(nom);
                if (entree.isDirectory() || (!estManifest && !estDonnees)) {
                    throw new InvalideException(Motif.CONTENU_INTERDIT,
                            "entrée inattendue : " + nom);
                }
                if ((estManifest && octetsManifest != null)
                        || (estDonnees && octetsDonnees != null)) {
                    throw new InvalideException(Motif.CONTENU_INVALIDE,
                            "entrée en double : " + nom);
                }
                byte[] octets = lireBorne(zip, MAX_OCTETS_ENTREE);
                if (estManifest) {
                    octetsManifest = octets;
                } else {
                    octetsDonnees = octets;
                }
            }
        } catch (InvalideException e) {
            throw e;
        } catch (IOException e) {
            // Inclut ZipException : ZIP abîmé ou tronqué.
            throw new InvalideException(Motif.FICHIER_ILLISIBLE, "archive illisible");
        }

        if (octetsManifest == null || octetsDonnees == null) {
            throw new InvalideException(Motif.FICHIER_ILLISIBLE,
                    "ce n'est pas une sauvegarde Fer Pro");
        }

        int versionFormat;
        String versionApp;
        long dateMs;
        String sommeAttendue;
        try {
            JSONObject manifest = new JSONObject(
                    new String(octetsManifest, StandardCharsets.UTF_8));
            if (!FORMAT.equals(manifest.optString("format", ""))) {
                throw new InvalideException(Motif.FORMAT_INCONNU, "format différent");
            }
            versionFormat = manifest.optInt("formatVersion", -1);
            if (versionFormat < 1) {
                throw new InvalideException(Motif.FORMAT_INCONNU, "version du format invalide");
            }
            if (versionFormat > VERSION_FORMAT) {
                throw new InvalideException(Motif.VERSION_TROP_RECENTE,
                        "format v" + versionFormat + " > v" + VERSION_FORMAT);
            }
            versionApp = manifest.optString("versionApp", "");
            dateMs = manifest.optLong("dateMs", 0L);
            sommeAttendue = manifest.optString("sha256", "");
        } catch (JSONException e) {
            throw new InvalideException(Motif.FICHIER_ILLISIBLE, "manifeste illisible");
        }

        if (!sha256Hex(octetsDonnees).equals(sommeAttendue)) {
            throw new InvalideException(Motif.SOMME_DE_CONTROLE,
                    "contenu abîmé ou modifié");
        }

        Map<String, Map<String, Object>> prefs = decoderDonnees(octetsDonnees);
        Apercu apercu = calculerApercu(prefs, versionFormat, versionApp, dateMs);
        return new Contenu(Collections.unmodifiableMap(prefs), apercu);
    }

    private static Map<String, Map<String, Object>> decoderDonnees(byte[] octets)
            throws InvalideException {
        Map<String, Map<String, Object>> resultat = new LinkedHashMap<>();
        try {
            JSONObject racine = new JSONObject(new String(octets, StandardCharsets.UTF_8));
            JSONObject jsonPrefs = racine.getJSONObject("prefs");
            if (jsonPrefs.length() > MAX_FICHIERS_PREFS) {
                throw new InvalideException(Motif.FICHIER_TROP_GRAND, "trop de fichiers de données");
            }

            Iterator<?> noms = jsonPrefs.keys();
            while (noms.hasNext()) {
                String nom = (String) noms.next();
                if (!NomsPrefs.estAutorise(nom)) {
                    throw new InvalideException(Motif.CONTENU_INTERDIT,
                            "fichier de données non autorisé : " + nom);
                }

                JSONObject jsonCles = jsonPrefs.getJSONObject(nom);
                Map<String, Object> cles = new LinkedHashMap<>();
                Iterator<?> iterateurCles = jsonCles.keys();
                while (iterateurCles.hasNext()) {
                    String cle = (String) iterateurCles.next();
                    if (cle.isEmpty() || cle.length() > MAX_LONGUEUR_CLE) {
                        throw new InvalideException(Motif.CONTENU_INVALIDE, "clé invalide");
                    }
                    Object valeur = decoderValeur(jsonCles.getJSONObject(cle));
                    if (valeur instanceof String) {
                        verifierFormeJson(cle, (String) valeur);
                    }
                    cles.put(cle, valeur);
                }
                resultat.put(nom, cles);
            }
        } catch (JSONException | ClassCastException e) {
            throw new InvalideException(Motif.CONTENU_INVALIDE, "données mal formées");
        }
        return resultat;
    }

    private static Object decoderValeur(JSONObject objet) throws InvalideException, JSONException {
        String type = objet.getString("t");
        Object v = objet.get("v");

        switch (type) {
            case T_TEXTE:
                if (v instanceof String) {
                    return v;
                }
                break;
            case T_LONG:
                if (v instanceof Number) {
                    return ((Number) v).longValue();
                }
                break;
            case T_ENTIER:
                if (v instanceof Number) {
                    long n = ((Number) v).longValue();
                    if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
                        return (int) n;
                    }
                }
                break;
            case T_BOOLEEN:
                if (v instanceof Boolean) {
                    return v;
                }
                break;
            case T_DECIMAL:
                if (v instanceof Number) {
                    return ((Number) v).floatValue();
                }
                break;
            case T_ENSEMBLE:
                if (v instanceof JSONArray) {
                    JSONArray tableau = (JSONArray) v;
                    Set<String> ensemble = new HashSet<>();
                    for (int i = 0; i < tableau.length(); i++) {
                        Object element = tableau.get(i);
                        if (!(element instanceof String)) {
                            throw new InvalideException(Motif.CONTENU_INVALIDE,
                                    "ensemble invalide");
                        }
                        ensemble.add((String) element);
                    }
                    return ensemble;
                }
                break;
            default:
                break;
        }
        throw new InvalideException(Motif.CONTENU_INVALIDE, "valeur invalide (type " + type + ")");
    }

    /** Les clés qui contiennent du JSON doivent contenir du JSON de la bonne forme. */
    private static void verifierFormeJson(String cle, String valeur) throws InvalideException {
        try {
            if (NomsPrefs.CLE_CLIENTS.equals(cle)
                    || NomsPrefs.CLE_CALCULS.equals(cle)
                    || NomsPrefs.CLE_HISTORIQUE.equals(cle)) {
                new JSONArray(valeur);
            } else if (NomsPrefs.CLE_STOCK.equals(cle)
                    || cle.startsWith(NomsPrefs.PREFIXE_OPERATION)) {
                new JSONObject(valeur);
            }
        } catch (JSONException e) {
            throw new InvalideException(Motif.CONTENU_INVALIDE, "JSON invalide pour " + cle);
        }
    }

    // ------------------------------------------------------------------
    // Aperçu, nom de fichier, outils
    // ------------------------------------------------------------------

    private static Apercu calculerApercu(Map<String, Map<String, Object>> prefs,
                                         int versionFormat, String versionApp, long dateMs) {
        int stocksClients = 0;
        for (String nom : prefs.keySet()) {
            if (NomsPrefs.estStockClient(nom)) {
                stocksClients++;
            }
        }
        return new Apercu(
                versionFormat,
                versionApp,
                dateMs,
                tailleJson(prefs, NomsPrefs.CLIENTS, NomsPrefs.CLE_CLIENTS, true),
                tailleJson(prefs, NomsPrefs.CALCULS, NomsPrefs.CLE_CALCULS, true),
                tailleJson(prefs, NomsPrefs.STOCK_GLOBAL, NomsPrefs.CLE_STOCK, false),
                stocksClients);
    }

    private static int tailleJson(Map<String, Map<String, Object>> prefs, String nomPrefs,
                                  String cle, boolean tableau) {
        Map<String, Object> fichier = prefs.get(nomPrefs);
        if (fichier == null) {
            return 0;
        }
        Object valeur = fichier.get(cle);
        if (!(valeur instanceof String)) {
            return 0;
        }
        try {
            return tableau
                    ? new JSONArray((String) valeur).length()
                    : new JSONObject((String) valeur).length();
        } catch (JSONException e) {
            return 0;
        }
    }

    /** Nom proposé pour le fichier, ex : FerPro_Sauvegarde_2026-11-06_14h32.ferpro. */
    public static String nomFichier(long dateMs) {
        return nomFichier(dateMs, TimeZone.getDefault());
    }

    static String nomFichier(long dateMs, TimeZone fuseau) {
        // Locale.ROOT : chiffres latins même si le téléphone est en arabe.
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm", Locale.ROOT);
        format.setTimeZone(fuseau);
        return "FerPro_Sauvegarde_" + format.format(new Date(dateMs)) + EXTENSION;
    }

    static String sha256Hex(byte[] octets) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(octets);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Lit un flux en refusant de dépasser la limite (arrêt dès que c'est trop gros). */
    private static byte[] lireBorne(InputStream flux, long limite)
            throws IOException, InvalideException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        byte[] tampon = new byte[8192];
        long total = 0;
        int lu;
        while ((lu = flux.read(tampon)) != -1) {
            total += lu;
            if (total > limite) {
                throw new InvalideException(Motif.FICHIER_TROP_GRAND, "taille maximale dépassée");
            }
            sortie.write(tampon, 0, lu);
        }
        return sortie.toByteArray();
    }
}
