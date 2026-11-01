package com.example.gkm_bar_fer.licence;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Point d'entrée unique du système de licence (même principe que StockManager,
 * ClientManager et CalculManager : singleton obtenu avec getInstance(context)).
 *
 * Séparation stricte : cette classe lit/écrit UNIQUEMENT le fichier "license_prefs"
 * (installation_id et license_code). Elle ne touche jamais aux clients, calculs,
 * stock, historique ou exports : l'expiration ne peut donc jamais effacer de données.
 */
public class LicenseManager {

    /** Source de l'heure : permet de tester l'expiration sans attendre. */
    interface Horloge {
        long maintenantMs();
    }

    /** Résultat d'une tentative d'activation / de renouvellement. */
    public static final class ActivationResult {

        public enum Etat {
            /** Licence valide, enregistrée. */
            SUCCES,
            /** Code refusé : voir getResultat() pour la raison. */
            REFUSE,
            /** Aucune clé publique dans l'application (LicenseConstants.PUBLIC_KEY_BASE32). */
            CONFIGURATION_MANQUANTE,
            /** Code valide mais impossible de l'écrire sur le téléphone. */
            ERREUR_STOCKAGE,
            /** Code valide, mais la date du téléphone est incohérente (retour en arrière). */
            DATE_INCORRECTE
        }

        private final Etat etat;
        private final LicenseVerifier.Resultat resultat;
        private final LicensePayload licence;

        private ActivationResult(Etat etat, LicenseVerifier.Resultat resultat,
                                 LicensePayload licence) {
            this.etat = etat;
            this.resultat = resultat;
            this.licence = licence;
        }

        public boolean estSucces() {
            return etat == Etat.SUCCES;
        }

        public Etat getEtat() {
            return etat;
        }

        /** Raison précise du refus (null si la configuration est manquante). */
        public LicenseVerifier.Resultat getResultat() {
            return resultat;
        }

        /** Contenu de la licence quand la signature est authentique (sinon null). */
        public LicensePayload getLicence() {
            return licence;
        }
    }

    private static LicenseManager instance;

    private final SharedPreferences preferences;
    private final LicenseVerifier verifier;
    private final Horloge horloge;
    private final ClockGuard clockGuard;

    /** Sans signature Keystore (utilisé par les tests). */
    LicenseManager(SharedPreferences preferences, LicenseVerifier verifier, Horloge horloge) {
        this(preferences, verifier, horloge, null);
    }

    LicenseManager(SharedPreferences preferences, LicenseVerifier verifier, Horloge horloge,
                   ClockGuard.Mac mac) {
        this.preferences = preferences;
        this.verifier = verifier;
        this.horloge = horloge;
        this.clockGuard = new ClockGuard(preferences, mac);
    }

    public static synchronized LicenseManager getInstance(Context context) {
        if (instance == null) {
            final Context appContext = context.getApplicationContext();
            SharedPreferences prefs = appContext
                    .getSharedPreferences(LicenseConstants.PREFS_NAME, Context.MODE_PRIVATE);
            instance = new LicenseManager(prefs, LicenseVerifier.integre(), new Horloge() {
                @Override
                public long maintenantMs() {
                    // Version release : heure réelle. Version debug : heure réelle + décalage
                    // de test (voir DebugClock dans src/debug et src/release).
                    return DebugClock.maintenantMs(appContext);
                }
            }, new KeystoreMac());
        }
        return instance;
    }

    /**
     * Retourne l'Installation ID de ce téléphone.
     * Il est créé au premier appel, puis conservé (même après redémarrage, expiration
     * ou renouvellement). Il n'est PAS sauvegardé dans le cloud Google.
     */
    public synchronized String getInstallationId() {
        String id = preferences.getString(LicenseConstants.KEY_INSTALLATION_ID, null);
        if (InstallationId.estValide(id)) {
            return id;
        }
        String nouveau = InstallationId.generer();
        // commit() (et non apply()) : on veut être sûr que l'ID est écrit sur le disque
        // avant de l'afficher au client.
        preferences.edit()
                .putString(LicenseConstants.KEY_INSTALLATION_ID, nouveau)
                .commit();
        return nouveau;
    }

    /** Calcule l'état actuel de la licence (vérification cryptographique complète). */
    public synchronized LicenseStatus getStatus() {
        String code = codeStocke();
        if (code == null) {
            return LicenseStatus.NOT_ACTIVATED;
        }
        if (verifier == null) {
            return LicenseStatus.INVALID;
        }
        String id = getInstallationId();
        LicenseVerifier.Verification v = verifier.verifier(code, id, jourActuel());
        switch (v.getResultat()) {
            case OK:
            case EXPIREE:
                // Licence authentique pour ce téléphone : on contrôle que l'horloge n'a pas
                // été reculée (même quand la licence est expirée, pour mémoriser la date vue).
                if (clockGuard.controler(horloge.maintenantMs(), id) != ClockGuard.Verdict.OK) {
                    return LicenseStatus.CLOCK_ERROR;
                }
                return v.getResultat() == LicenseVerifier.Resultat.OK
                        ? LicenseStatus.VALID : LicenseStatus.EXPIRED;
            case PAS_ENCORE_VALIDE:
                // Le code avait été accepté à l'activation : si la licence "n'a pas commencé",
                // c'est que la date du téléphone a été reculée.
                return LicenseStatus.CLOCK_ERROR;
            default:
                return LicenseStatus.INVALID;
        }
    }

    /**
     * Contenu de la licence enregistrée (dates, type), ou null s'il n'y en a pas
     * ou si elle est illisible. À utiliser pour afficher la date d'expiration.
     */
    public synchronized LicensePayload getLicence() {
        String code = codeStocke();
        if (code == null || verifier == null) {
            return null;
        }
        return verifier.verifier(code, getInstallationId(), jourActuel()).getLicence();
    }

    /**
     * Active (ou renouvelle) la licence avec un code saisi par le client.
     * Si le code est valide pour CE téléphone, il remplace l'ancien. Sinon rien ne change.
     * Les données métier ne sont jamais touchées.
     */
    public synchronized ActivationResult activer(String codeSaisi) {
        if (verifier == null) {
            return new ActivationResult(ActivationResult.Etat.CONFIGURATION_MANQUANTE, null, null);
        }
        String id = getInstallationId();
        LicenseVerifier.Verification v = verifier.verifier(codeSaisi, id, jourActuel());
        if (!v.estOk()) {
            return new ActivationResult(ActivationResult.Etat.REFUSE, v.getResultat(), v.getLicence());
        }
        LicensePayload ancienne = getLicence(); // licence actuelle, avant remplacement
        boolean ecrit = preferences.edit()
                .putString(LicenseConstants.KEY_LICENSE_CODE, formeCanonique(codeSaisi))
                .commit();
        if (!ecrit) {
            return new ActivationResult(ActivationResult.Etat.ERREUR_STOCKAGE, v.getResultat(), v.getLicence());
        }
        // Une licence réellement NOUVELLE permet de repartir d'une date de référence propre.
        // Re-saisir une ancienne licence ne remet rien à zéro (sinon on pourrait annuler
        // la protection en recollant un vieux code après avoir reculé la date).
        if (ancienne == null || estPlusRecente(v.getLicence(), ancienne)) {
            clockGuard.reinitialiser(horloge.maintenantMs(), id);
        }
        if (getStatus() == LicenseStatus.CLOCK_ERROR) {
            return new ActivationResult(ActivationResult.Etat.DATE_INCORRECTE, v.getResultat(), v.getLicence());
        }
        return new ActivationResult(ActivationResult.Etat.SUCCES, v.getResultat(), v.getLicence());
    }

    /** Une licence est "plus récente" si elle commence plus tard ou se termine plus tard. */
    private static boolean estPlusRecente(LicensePayload nouvelle, LicensePayload ancienne) {
        if (nouvelle.getJourDebut() > ancienne.getJourDebut()) {
            return true;
        }
        if (nouvelle.estAVie() && !ancienne.estAVie()) {
            return true;
        }
        return !nouvelle.estAVie() && !ancienne.estAVie()
                && nouvelle.getJourFin() > ancienne.getJourFin();
    }

    private String codeStocke() {
        String code = preferences.getString(LicenseConstants.KEY_LICENSE_CODE, null);
        return code == null || code.trim().isEmpty() ? null : code;
    }

    private int jourActuel() {
        return LicenseDates.jourEpochDepuisMillis(horloge.maintenantMs());
    }

    /** Écrit le code toujours de la même façon, quelle que soit la façon dont il a été collé. */
    private static String formeCanonique(String code) {
        return Base32.grouper(Base32.encode(Base32.decode(code)), LicenseConstants.TAILLE_GROUPE_CODE);
    }
}