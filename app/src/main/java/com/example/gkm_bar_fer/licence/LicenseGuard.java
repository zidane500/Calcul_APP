package com.example.gkm_bar_fer.licence;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import com.example.gkm_bar_fer.SplashActivity;

/**
 * Garde de licence : contrôle la licence à CHAQUE fois qu'un écran de l'application
 * passe au premier plan (démarrage, retour depuis une autre application, réouverture
 * après que le téléphone a fermé l'application en arrière-plan...).
 *
 * Pourquoi : Android peut rouvrir directement un écran (calcul, stock...) sans passer
 * par le Splash. Un contrôle uniquement au démarrage serait donc contournable.
 *
 * Elle est enregistrée UNE SEULE FOIS dans GkmApplication : aucun écran existant
 * n'a besoin d'être modifié (et les futurs écrans seront protégés automatiquement).
 */
public final class LicenseGuard implements Application.ActivityLifecycleCallbacks {

    /** Écrans qui gèrent eux-mêmes la licence : on ne les contrôle pas (sinon boucle infinie). */
    private static boolean estExempte(Activity activity) {
        return activity instanceof SplashActivity
                || activity instanceof ActivationActivity;
    }

    /** Écran à afficher quand la licence ne permet pas d'utiliser l'application. */
    static Class<? extends Activity> ecranPour(LicenseStatus statut) {
        // Étapes 8 et 9 : écran "Licence expirée" dédié. Pour l'instant : écran d'activation.
        return ActivationActivity.class;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (estExempte(activity)) {
            return;
        }
        LicenseStatus statut = LicenseManager.getInstance(activity).getStatus();
        if (statut.autoriseAcces()) {
            return;
        }
        // Licence absente, expirée ou invalide : retour à l'écran de licence, et on vide
        // la pile d'écrans pour que le bouton Retour ne ramène pas dans l'application.
        Intent intent = new Intent(activity, ecranPour(statut));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    // Méthodes obligatoires de l'interface (rien à faire ici).

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}