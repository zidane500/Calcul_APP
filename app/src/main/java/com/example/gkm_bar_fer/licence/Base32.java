package com.example.gkm_bar_fer.licence;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * Encodage Base32 (alphabet RFC 4648 : A-Z et 2-7), sans padding.
 * Écrit à la main car Android 7 (minSdk 24) n'a pas de Base32 intégré.
 * Pourquoi Base32 : uniquement des lettres MAJUSCULES et des chiffres, sans
 * confusion possible entre majuscules/minuscules quand le code est recopié.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
        // Classe utilitaire : pas d'instance.
    }

    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    /**
     * Décode une chaîne Base32 (espaces, tirets et '=' ignorés, minuscules acceptées).
     *
     * @return les octets, ou null si un caractère invalide est rencontré.
     */
    public static byte[] decode(String texte) {
        if (texte == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        String propre = texte.toUpperCase(Locale.ROOT);
        for (int i = 0; i < propre.length(); i++) {
            char c = propre.charAt(i);
            if (c == '=' || c == '-' || Character.isWhitespace(c)) {
                continue;
            }
            int valeur = ALPHABET.indexOf(c);
            if (valeur < 0) {
                return null;
            }
            buffer = (buffer << 5) | valeur;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
                buffer &= (1 << bits) - 1;
            }
        }
        return out.toByteArray();
    }

    /** Découpe un texte en groupes séparés par des tirets : ABCDEF-GHIJKL-... */
    public static String grouper(String texte, int tailleGroupe) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texte.length(); i += tailleGroupe) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(texte, i, Math.min(i + tailleGroupe, texte.length()));
        }
        return sb.toString();
    }
}