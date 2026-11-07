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
 *  - quand l'app passe en arrière-plan (couvre aussi les stocks des clients).
 *
 * Les déclenchements rapprochés sont regroupés (voir SauvegardeRegles) et le travail se fait sur
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

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService TRAVAIL = Executors.newSingleThreadExecutor();
    private static final Runnable TACHE = new Runnable() {
        @Override
        public void run() {
            TRAVAIL.execute(new Runnable() {
                @Override
                public void run() {
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

        // Passage en arrière-plan (ou changement d'écran) : on planifie une sauvegarde.
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStopped(Activity activity) {
                planifier();
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

    private static void planifier() {
        long delai = SauvegardeRegles.delai(System.currentTimeMillis(), derniereEcritureMs);
        HANDLER.removeCallbacks(TACHE);
        HANDLER.postDelayed(TACHE, delai);
    }

    /** Exécutée sur le thread de travail. */
    private static void executer() {
        try {
            Context ctx = contexte;
            Destination destination = creerDestination(ctx);
            if (destination == null) {
                return;   // pas d'autorisation (Android 7 à 9) : on réessaiera plus tard
            }

            MemoirePrefs memoire = new MemoirePrefs(
                    ctx.getSharedPreferences(PREFS_MEMOIRE, Context.MODE_PRIVATE));

            MoteurSauvegarde.Resultat resultat = new MoteurSauvegarde(destination, memoire).executer(
                    SauvegardeDonnees.instantane(ctx),
                    SauvegardeDonnees.versionApp(ctx),
                    System.currentTimeMillis());

            if (resultat == MoteurSauvegarde.Resultat.REUSSIE) {
                derniereEcritureMs = memoire.derniereDate();
            }
            Log.d(TAG, "Sauvegarde automatique : " + resultat);
        } catch (Throwable erreur) {
            Log.w(TAG, "Sauvegarde automatique impossible", erreur);
        }
    }

    private static Destination creerDestination(Context ctx) {
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