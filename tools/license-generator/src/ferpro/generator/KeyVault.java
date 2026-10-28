package ferpro.generator;

import com.example.gkm_bar_fer.licence.Base32;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Coffre de la clé privée Fer Pro (sur TON PC uniquement).
 *
 * La clé privée est stockée dans un fichier chiffré par mot de passe :
 *   mot de passe -> PBKDF2-HMAC-SHA256 (210 000 itérations) -> clé AES-256 -> AES-GCM.
 * Sans le mot de passe, le fichier est inutilisable. Avec un mauvais mot de passe,
 * le déchiffrement échoue (GCM détecte l'erreur).
 */
public final class KeyVault {

    private static final byte[] MAGIC = {'F', 'P', 'K', '1'};
    private static final int TAILLE_SEL = 16;
    private static final int TAILLE_IV = 12;
    private static final int ITERATIONS = 210_000;
    private static final int BITS_CLE = 256;
    private static final int BITS_TAG = 128;

    public static final String NOM_FICHIER_PRIVE = "cle-privee-ferpro.key";
    public static final String NOM_FICHIER_PUBLIC = "cle-publique-ferpro.txt";
    public static final String NOM_JOURNAL = "journal-licences.csv";

    /** Paire de clés (privée + publique). */
    public static final class Cles {
        public final PrivateKey privee;
        public final PublicKey publique;

        Cles(PrivateKey privee, PublicKey publique) {
            this.privee = privee;
            this.publique = publique;
        }
    }

    private KeyVault() {
        // Classe utilitaire : pas d'instance.
    }

    /** Dossier par défaut : C:\Users\TonNom\FerPro-Licences (hors de tout projet Git). */
    public static Path dossierParDefaut() {
        return Paths.get(System.getProperty("user.home"), "FerPro-Licences");
    }

    public static Cles generer() throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair paire = gen.generateKeyPair();
        return new Cles(paire.getPrivate(), paire.getPublic());
    }

    /** Clé publique X.509 en Base32 : c'est ce texte qu'on colle dans l'application Android. */
    public static String clePubliqueBase32(PublicKey cle) {
        return Base32.encode(cle.getEncoded());
    }

    /** Enregistre la paire de clés chiffrée. Refuse d'écraser un fichier existant. */
    public static void sauvegarder(Path fichier, Cles cles, char[] motDePasse)
            throws IOException, GeneralSecurityException {
        byte[] priv = cles.privee.getEncoded();
        byte[] pub = cles.publique.getEncoded();
        ByteArrayOutputStream clair = new ByteArrayOutputStream();
        DataOutputStream sortie = new DataOutputStream(clair);
        sortie.writeInt(priv.length);
        sortie.write(priv);
        sortie.writeInt(pub.length);
        sortie.write(pub);
        sortie.flush();

        SecureRandom alea = new SecureRandom();
        byte[] sel = new byte[TAILLE_SEL];
        byte[] iv = new byte[TAILLE_IV];
        alea.nextBytes(sel);
        alea.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriver(motDePasse, sel), new GCMParameterSpec(BITS_TAG, iv));
        cipher.updateAAD(MAGIC);
        byte[] chiffre = cipher.doFinal(clair.toByteArray());

        ByteArrayOutputStream fichierOctets = new ByteArrayOutputStream();
        fichierOctets.write(MAGIC);
        fichierOctets.write(sel);
        fichierOctets.write(iv);
        fichierOctets.write(chiffre);

        Files.createDirectories(fichier.toAbsolutePath().getParent());
        Files.write(fichier, fichierOctets.toByteArray(), StandardOpenOption.CREATE_NEW);

        Arrays.fill(priv, (byte) 0);
        Arrays.fill(clair.toByteArray(), (byte) 0);
    }

    /**
     * Charge la paire de clés.
     *
     * @throws GeneralSecurityException si le mot de passe est incorrect ou le fichier corrompu.
     */
    public static Cles charger(Path fichier, char[] motDePasse)
            throws IOException, GeneralSecurityException {
        byte[] octets = Files.readAllBytes(fichier);
        int entete = MAGIC.length + TAILLE_SEL + TAILLE_IV;
        if (octets.length <= entete) {
            throw new GeneralSecurityException("Fichier de clé invalide.");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (octets[i] != MAGIC[i]) {
                throw new GeneralSecurityException("Ce fichier n'est pas une clé Fer Pro.");
            }
        }
        byte[] sel = Arrays.copyOfRange(octets, MAGIC.length, MAGIC.length + TAILLE_SEL);
        byte[] iv = Arrays.copyOfRange(octets, MAGIC.length + TAILLE_SEL, entete);
        byte[] chiffre = Arrays.copyOfRange(octets, entete, octets.length);

        byte[] clair;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriver(motDePasse, sel), new GCMParameterSpec(BITS_TAG, iv));
            cipher.updateAAD(MAGIC);
            clair = cipher.doFinal(chiffre);
        } catch (AEADBadTagException e) {
            throw new GeneralSecurityException("Mot de passe incorrect (ou fichier de clé corrompu).");
        }

        DataInputStream entree = new DataInputStream(new ByteArrayInputStream(clair));
        byte[] priv = new byte[entree.readInt()];
        entree.readFully(priv);
        byte[] pub = new byte[entree.readInt()];
        entree.readFully(pub);

        KeyFactory usine = KeyFactory.getInstance("EC");
        PrivateKey clePrivee = usine.generatePrivate(new PKCS8EncodedKeySpec(priv));
        PublicKey clePublique = usine.generatePublic(new X509EncodedKeySpec(pub));
        Arrays.fill(priv, (byte) 0);
        Arrays.fill(clair, (byte) 0);
        return new Cles(clePrivee, clePublique);
    }

    private static SecretKeySpec deriver(char[] motDePasse, byte[] sel) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(motDePasse, sel, ITERATIONS, BITS_CLE);
        try {
            byte[] cle = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return new SecretKeySpec(cle, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}