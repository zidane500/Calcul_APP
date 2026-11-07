package com.example.gkm_bar_fer.sauvegarde;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Destination pour Android 10 et plus : dossier public Documents/FerPro/ via MediaStore.
 * Aucune permission n'est nécessaire pour les fichiers que l'app crée elle-même.
 *
 * Le fichier est créé "en attente" (IS_PENDING = 1) : il reste invisible tant qu'il n'est pas
 * entièrement écrit, puis il apparaît d'un coup. Pas de fichier à moitié écrit dans le dossier.
 *
 * Après une désinstallation, la nouvelle installation ne "possède" plus les anciens fichiers :
 * elle ne les voit pas dans lister() et ne peut ni les modifier ni les supprimer. Ils restent
 * dans le dossier et le client les retrouve avec le sélecteur de fichiers pour restaurer.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
final class DestinationMediaStore implements Destination {

    private static final String DOSSIER = "Documents/FerPro/";
    private static final String TYPE_MIME = "application/octet-stream";

    private final ContentResolver resolver;
    private final Uri collection;

    DestinationMediaStore(Context contexte) {
        this.resolver = contexte.getApplicationContext().getContentResolver();
        this.collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    }

    @Override
    public List<Entree> lister() throws IOException {
        List<Entree> resultat = new ArrayList<>();
        String[] colonnes = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME};
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] arguments = {"Documents/FerPro%"};

        try (Cursor curseur = resolver.query(collection, colonnes, selection, arguments,
                MediaStore.MediaColumns.DISPLAY_NAME + " DESC")) {
            if (curseur == null) {
                throw new IOException("Requête MediaStore impossible");
            }
            while (curseur.moveToNext()) {
                String nom = curseur.getString(1);
                if (SauvegardeRegles.estNomSauvegarde(nom)) {
                    resultat.add(new Entree(nom,
                            ContentUris.withAppendedId(collection, curseur.getLong(0))));
                }
            }
        }
        return resultat;
    }

    @Override
    public Entree creer(String nom, byte[] octets) throws IOException {
        ContentValues valeurs = new ContentValues();
        valeurs.put(MediaStore.MediaColumns.DISPLAY_NAME, nom);
        valeurs.put(MediaStore.MediaColumns.MIME_TYPE, TYPE_MIME);
        valeurs.put(MediaStore.MediaColumns.RELATIVE_PATH, DOSSIER);
        valeurs.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(collection, valeurs);
        if (uri == null) {
            throw new IOException("Création du fichier impossible");
        }

        try (OutputStream sortie = resolver.openOutputStream(uri, "wt")) {
            if (sortie == null) {
                throw new IOException("Ouverture du fichier impossible");
            }
            sortie.write(octets);
            sortie.flush();
        } catch (IOException | RuntimeException e) {
            resolver.delete(uri, null, null);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }

        // Le fichier est complet : il devient visible.
        ContentValues fin = new ContentValues();
        fin.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, fin, null, null);

        // Android peut avoir ajouté " (1)" au nom en cas de doublon : on prend le nom réel.
        String nomReel = nom;
        try (Cursor curseur = resolver.query(uri,
                new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (curseur != null && curseur.moveToFirst()) {
                nomReel = curseur.getString(0);
            }
        }
        return new Entree(nomReel, uri);
    }

    @Override
    public InputStream ouvrir(Entree entree) throws IOException {
        InputStream flux = resolver.openInputStream((Uri) entree.ref);
        if (flux == null) {
            throw new IOException("Ouverture du fichier impossible");
        }
        return flux;
    }

    @Override
    public void supprimer(Entree entree) throws IOException {
        resolver.delete((Uri) entree.ref, null, null);
    }
}
