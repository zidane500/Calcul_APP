package com.example.gkm_bar_fer.sauvegarde;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cœur de la sauvegarde automatique : décide s'il faut écrire une sauvegarde, l'écrit, la relit
 * pour vérifier qu'elle est bonne, puis ne garde que les dernières versions.
 *
 * Aucune dépendance Android : la destination (dossier) et la mémoire (ce qui a déjà été fait)
 * sont fournies de l'extérieur, ce qui permet de tout tester sur PC.
 *
 * Garanties :
 *  - jamais d'exception vers l'app : toute erreur devient Resultat.ECHEC ;
 *  - jamais de sauvegarde vide, ni de doublon d'une sauvegarde identique ;
 *  - un fichier qui ne se relit pas correctement est supprimé tout de suite ;
 *  - seuls les fichiers créés par cette installation sont supprimés (jamais ceux d'une
 *    installation précédente, qui sont peut-être le seul exemplaire des données du client).
 */
public final class MoteurSauvegarde {

    public enum Resultat {
        /** Une nouvelle sauvegarde a été écrite et vérifiée. */
        REUSSIE,
        /** Rien n'a changé depuis la dernière sauvegarde : rien à écrire. */
        INCHANGEE,
        /** Aucune donnée à protéger : rien n'est écrit. */
        VIDE,
        /** Impossible d'écrire ou de vérifier : l'app continue normalement. */
        ECHEC
    }

    /** Ce que cette installation a déjà fait (conservé entre deux lancements de l'app). */
    public interface Memoire {
        String empreinte();

        String dernierNom();

        long derniereDate();

        /** Note qu'un fichier vient d'être créé et que ses données avaient cette empreinte. */
        void enregistrer(String empreinte, String nom, long dateMs);

        /** @return true si ce fichier a été créé par cette installation. */
        boolean aCree(String nom);

        void oublier(String nom);
    }

    private final Destination destination;
    private final Memoire memoire;

    public MoteurSauvegarde(Destination destination, Memoire memoire) {
        this.destination = destination;
        this.memoire = memoire;
    }

    public Resultat executer(Map<String, ? extends Map<String, ?>> instantane,
                             String versionApp, long maintenantMs) {
        try {
            byte[] octets = SauvegardeCodec.ecrire(instantane, versionApp, maintenantMs);

            // Vérification en mémoire : le fichier qu'on s'apprête à écrire est lisible.
            SauvegardeCodec.Contenu contenu = SauvegardeCodec.lire(octets);
            if (SauvegardeRegles.estVide(contenu.apercu)) {
                return Resultat.VIDE;
            }

            String empreinte = SauvegardeCodec.empreinte(instantane);
            if (empreinte.equals(memoire.empreinte())
                    && contientNom(destination.lister(), memoire.dernierNom())) {
                return Resultat.INCHANGEE;
            }

            Destination.Entree nouvelle =
                    destination.creer(SauvegardeCodec.nomFichier(maintenantMs), octets);

            // Vérification sur le disque : on relit le fichier écrit et on compare le contenu.
            if (!relectureIdentique(nouvelle, empreinte)) {
                supprimerSansErreur(nouvelle);
                return Resultat.ECHEC;
            }

            memoire.enregistrer(empreinte, nouvelle.nom, maintenantMs);
            faireTourner(nouvelle);
            return Resultat.REUSSIE;

        } catch (IOException | SauvegardeCodec.InvalideException | RuntimeException e) {
            return Resultat.ECHEC;
        }
    }

    private boolean relectureIdentique(Destination.Entree entree, String empreinteAttendue) {
        try (InputStream flux = destination.ouvrir(entree)) {
            SauvegardeCodec.Contenu relu = SauvegardeCodec.lire(flux);
            return empreinteAttendue.equals(SauvegardeCodec.empreinte(relu.prefs));
        } catch (IOException | SauvegardeCodec.InvalideException | RuntimeException e) {
            return false;
        }
    }

    /** Ne garde que les dernières versions créées par cette installation. */
    private void faireTourner(Destination.Entree nouvelle) {
        try {
            List<Destination.Entree> miennes = new ArrayList<>();
            for (Destination.Entree entree : destination.lister()) {
                if (memoire.aCree(entree.nom)) {
                    miennes.add(entree);
                }
            }

            List<String> noms = new ArrayList<>();
            for (Destination.Entree entree : miennes) {
                noms.add(entree.nom);
            }
            List<String> aSupprimer =
                    SauvegardeRegles.aSupprimer(noms, SauvegardeRegles.NB_VERSIONS_GARDEES);

            for (Destination.Entree entree : miennes) {
                if (aSupprimer.contains(entree.nom) && !entree.nom.equals(nouvelle.nom)) {
                    supprimerSansErreur(entree);
                    memoire.oublier(entree.nom);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Le ménage échouera peut-être, la sauvegarde elle-même est déjà bonne.
        }
    }

    private static boolean contientNom(List<Destination.Entree> entrees, String nom) {
        if (nom == null) {
            return false;
        }
        for (Destination.Entree entree : entrees) {
            if (nom.equals(entree.nom)) {
                return true;
            }
        }
        return false;
    }

    private void supprimerSansErreur(Destination.Entree entree) {
        try {
            destination.supprimer(entree);
        } catch (IOException | RuntimeException e) {
            // ignoré : au pire un fichier en trop
        }
    }
}
