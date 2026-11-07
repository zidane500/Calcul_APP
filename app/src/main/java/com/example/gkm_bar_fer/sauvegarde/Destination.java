package com.example.gkm_bar_fer.sauvegarde;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Endroit où sont écrites les sauvegardes automatiques (dossier Documents/FerPro/).
 * Deux versions réelles (Android 10+ : MediaStore ; Android 7 à 9 : fichiers) et une version
 * en mémoire pour les tests.
 */
public interface Destination {

    /** Un fichier de sauvegarde existant : son nom et une référence propre à la destination. */
    final class Entree {
        public final String nom;
        public final Object ref;

        public Entree(String nom, Object ref) {
            this.nom = nom;
            this.ref = ref;
        }
    }

    /** Fichiers de sauvegarde Fer Pro visibles, du plus récent au plus ancien (tri par nom). */
    List<Entree> lister() throws IOException;

    /**
     * Crée le fichier. Il ne doit devenir visible qu'une fois entièrement écrit
     * (jamais de fichier à moitié écrit dans le dossier).
     */
    Entree creer(String nom, byte[] octets) throws IOException;

    InputStream ouvrir(Entree entree) throws IOException;

    void supprimer(Entree entree) throws IOException;
}
