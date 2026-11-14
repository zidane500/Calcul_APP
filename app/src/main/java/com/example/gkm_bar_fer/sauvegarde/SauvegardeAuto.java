package com.example.gkm_bar_fer.sauvegarde;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sauvegarde automatique des données dans Documents/FerPro/.
 *
 * Elle se déclenche toute seule :
 *  - au démarrage de l'app ;
 *  - après chaque modification des clients, des calculs ou du stock global ;
 *  - tout de suite quand l'app passe en arrière-plan (couvre aussi les stocks des clients).
 *
 * Les modifications rapprochées sont regroupées (voir SauvegardeRegles) et le travail se fait sur
 * un thread à part : l'écran n'est jamais ralenti. Une erreur ne fait jamais planter l'app.
 *
 * Mise en route : SauvegardeAuto.demarrer(this) dans GkmApplication.onCreate().
 */
public final class SauvegardeAuto {

    private static final String TAG = "FerProSauvegarde";

    /** Mémoire de la sauvegarde automatique. Ce fichier n'est PAS sauvegardé (hors liste blanche). */
    static final String PREFS_MEMOIRE = "sauvegarde_auto_prefs";

    private static final String CLE_EMPREINTE = "empreinte";
    private static final String CLE_DERNIER_NOM = "dernier_nom";
    private static final String CLE_DERNIERE_DATE = "derniere_date";
    private static final String CLE_NOMS_CREES = "noms_crees";
    private static final String CLE_PERMISSION_DEMANDEE = "permission_demandee";

    private static final int CODE_PERMISSION = 4210;

    static final Handler HANDLER = new Handler(Looper.getMainLooper());
    static final ExecutorService TRAVAIL = Executors.newSingleThreadExecutor();
    private static final Runnable TACHE = new Runnable() {
        @Override
        public void run() {
            TRAVAIL.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Sauvegarde automatique : declenchee (modification)");
                    executer();
                }
            });
        }
    };

    // Références gardées en vie : Android ne retient les écouteurs de préférences que faiblement.
    private static final List<SharedPreferences.OnSharedPreferenceChangeListener> ECOUTEURS =
            new ArrayList<>();

    private static Context contexte;
    private static boolean demarree = false;
    private static volatile long derniereEcritureMs = 0L;

    private SauvegardeAuto() {
        // Classe utilitaire : pas d'instance.
    }

    /** À appeler une seule fois, au démarrage de l'application. */
    public static synchronized void demarrer(Application application) {
        if (demarree) {
            return;
        }
        demarree = true;
        contexte = application.getApplicationContext();
        derniereEcritureMs = new MemoirePrefs(
                contexte.getSharedPreferences(PREFS_MEMOIRE, Context.MODE_PRIVATE)).derniereDate();

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStopped(Activity activity) {
                // L'app part en arrière-plan : Android peut figer le processus quelques secondes
                // plus tard, et une tâche différée ne s'exécuterait alors jamais. On sauvegarde
                // donc tout de suite (sans attendre).
                sauvegarderMaintenant("arriere-plan");
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });

        // Chaque modification des données principales planifie une sauvegarde.
        String[] fichiers = {NomsPrefs.CLIENTS, NomsPrefs.CALCULS, NomsPrefs.STOCK_GLOBAL};
        for (String nom : fichiers) {
            SharedPreferences.OnSharedPreferenceChangeListener ecouteur =
                    new SharedPreferences.OnSharedPreferenceChangeListener() {
                        @Override
                        public void onSharedPreferenceChanged(SharedPreferences p, String cle) {
                            planifier();
                        }
                    };
            contexte.getSharedPreferences(nom, Context.MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(ecouteur);
            ECOUTEURS.add(ecouteur);
        }

        Log.d(TAG, "Sauvegarde automatique demarree");
        planifier();   // premier lancement, ou lancement après une pause
    }

    /**
     * Android 7 à 9 seulement : demande UNE fois l'autorisation d'écrire dans Documents.
     * Android 10 et plus : rien à demander. À appeler depuis l'écran d'accueil.
     */
    public static void demanderPermissionSiNecessaire(Activity activite) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return;
        }
        if (ContextCompat.checkSelfPermission(activite, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences prefs = activite.getSharedPreferences(PREFS_MEMOIRE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(CLE_PERMISSION_DEMANDEE, false)) {
            return;   // une seule demande : pas d'insistance
        }
        prefs.edit().putBoolean(CLE_PERMISSION_DEMANDEE, true).apply();
        ActivityCompat.requestPermissions(activite,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, CODE_PERMISSION);
    }

    /** Lance tout de suite une sauvegarde (thread de travail) : rien n'est écrit si rien n'a changé. */
    private static void sauvegarderMaintenant(final String raison) {
        HANDLER.removeCallbacks(TACHE);          // la tâche différée devient inutile
        TRAVAIL.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Sauvegarde automatique : declenchee (" + raison + ")");
                executer();
            }
        });
    }

    private static void planifier() {
        long delai = SauvegardeRegles.delai(System.currentTimeMillis(), derniereEcritureMs);
        HANDLER.removeCallbacks(TACHE);
        HANDLER.postDelayed(TACHE, delai);
    }

    /** Exécutée sur le thread de travail. */
    /**
     * Exécutée sur le thread de travail.
     * @return le résultat, ou null si l'autorisation de stockage manque (Android 7 à 9)
     */
    static MoteurSauvegarde.Resultat executer() {
        try {
            Context ctx = contexte;
            Destination destination = creerDestination(ctx);
            if (destination == null) {
                Log.d(TAG, "Sauvegarde automatique : autorisation de stockage manquante");
                return null;
            }

            MemoirePrefs memoire = new MemoirePrefs(
                    ctx.getSharedPreferences(PREFS_MEMOIRE, Context.MODE_PRIVATE));

            MoteurSauvegarde moteur = new MoteurSauvegarde(destination, memoire);
            MoteurSauvegarde.Resultat resultat = moteur.executer(
                    SauvegardeDonnees.instantane(ctx),
                    SauvegardeDonnees.versionApp(ctx),
                    System.currentTimeMillis());

            if (resultat == MoteurSauvegarde.Resultat.REUSSIE) {
                derniereEcritureMs = memoire.derniereDate();
            }
            Log.d(TAG, "Sauvegarde automatique : " + resultat);
            if (resultat == MoteurSauvegarde.Resultat.ECHEC) {
                Log.w(TAG, "Cause de l'echec : " + moteur.derniereErreur());
            }
            return resultat;
        } catch (Throwable erreur) {
            Log.w(TAG, "Sauvegarde automatique impossible", erreur);
            return MoteurSauvegarde.Resultat.ECHEC;
        }
    }

    static Destination creerDestination(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new DestinationMediaStore(ctx);
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        return new DestinationFichiers();
    }

    /** Mémoire de la sauvegarde automatique, dans un petit fichier de préférences à part. */
    static final class MemoirePrefs implements MoteurSauvegarde.Memoire {

        private final SharedPreferences prefs;

        MemoirePrefs(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public String empreinte() {
            return prefs.getString(CLE_EMPREINTE, null);
        }

        @Override
        public String dernierNom() {
            return prefs.getString(CLE_DERNIER_NOM, null);
        }

        @Override
        public long derniereDate() {
            return prefs.getLong(CLE_DERNIERE_DATE, 0L);
        }

        @Override
        public void enregistrer(String empreinte, String nom, long dateMs) {
            Set<String> noms = new HashSet<>(prefs.getStringSet(CLE_NOMS_CREES, new HashSet<String>()));
            noms.add(nom);
            prefs.edit()
                    .putString(CLE_EMPREINTE, empreinte)
                    .putString(CLE_DERNIER_NOM, nom)
                    .putLong(CLE_DERNIERE_DATE, dateMs)
                    .putStringSet(CLE_NOMS_CREES, noms)
                    .apply();
        }

        @Override
        public boolean aCree(String nom) {
            return prefs.getStringSet(CLE_NOMS_CREES, new HashSet<String>()).contains(nom);
        }

        @Override
        public void oublier(String nom) {
            Set<String> noms = new HashSet<>(prefs.getStringSet(CLE_NOMS_CREES, new HashSet<String>()));
            if (noms.remove(nom)) {
                prefs.edit().putStringSet(CLE_NOMS_CREES, noms).apply();
            }
        }
    }
}