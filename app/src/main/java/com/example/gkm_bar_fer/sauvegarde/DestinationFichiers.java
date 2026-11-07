package com.example.gkm_bar_fer.sauvegarde;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Destination pour Android 7 à 9 : dossier public Documents/FerPro/ avec de simples fichiers.
 * Demande l'autorisation de stockage (WRITE_EXTERNAL_STORAGE, limitée à Android 9 dans le manifeste).
 *
 * Écriture sûre : le fichier est écrit sous un nom temporaire puis renommé une fois complet.
 */
@SuppressWarnings("deprecation")   // getExternalStoragePublicDirectory : seulement utilisé avant Android 10
final class DestinationFichiers implements Destination {

    private static final String EXTENSION_TEMPORAIRE = ".tmp";

    private final File dossier;

    DestinationFichiers() {
        this.dossier = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "FerPro");
    }

    @Override
    public List<Entree> lister() throws IOException {
        List<Entree> resultat = new ArrayList<>();
        File[] fichiers = dossier.listFiles();
        if (fichiers == null) {
            return resultat;      // dossier pas encore créé
        }
        List<String> noms = new ArrayList<>();
        for (File fichier : fichiers) {
            if (SauvegardeRegles.estNomSauvegarde(fichier.getName())) {
                noms.add(fichier.getName());
            }
        }
        Collections.sort(noms, Collections.<String>reverseOrder());   // du plus récent au plus ancien
        for (String nom : noms) {
            resultat.add(new Entree(nom, new File(dossier, nom)));
        }
        return resultat;
    }

    @Override
    public Entree creer(String nom, byte[] octets) throws IOException {
        if (!dossier.isDirectory() && !dossier.mkdirs() && !dossier.isDirectory()) {
            throw new IOException("Création du dossier impossible");
        }

        File definitif = new File(dossier, nom);
        File temporaire = new File(dossier, nom + EXTENSION_TEMPORAIRE);

        try (FileOutputStream sortie = new FileOutputStream(temporaire)) {
            sortie.write(octets);
            sortie.flush();
            sortie.getFD().sync();            // vraiment écrit sur le disque avant le renommage
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temporaire.delete();
            throw e;
        }

        if (definitif.exists() && !definitif.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temporaire.delete();
            throw new IOException("Remplacement du fichier impossible");
        }
        if (!temporaire.renameTo(definitif)) {
            //noinspection ResultOfMethodCallIgnored
            temporaire.delete();
            throw new IOException("Renommage du fichier impossible");
        }
        return new Entree(nom, definitif);
    }

    @Override
    public InputStream ouvrir(Entree entree) throws IOException {
        return new FileInputStream((File) entree.ref);
    }

    @Override
    public void supprimer(Entree entree) throws IOException {
        File fichier = (File) entree.ref;
        if (fichier.exists() && !fichier.delete()) {
            throw new IOException("Suppression impossible : " + fichier.getName());
        }
    }
}
