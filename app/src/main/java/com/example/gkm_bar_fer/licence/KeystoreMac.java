package com.example.gkm_bar_fer.licence;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * Signature HMAC-SHA256 avec une clé créée dans l'Android Keystore.
 *
 * La clé est générée sur le téléphone, ne peut pas être exportée, n'est pas incluse dans
 * les sauvegardes et disparaît à la désinstallation. Aucune dépendance Gradle : le
 * Keystore fait partie d'Android (API 23+, ton minSdk est 24).
 */
final class KeystoreMac implements ClockGuard.Mac {

    private static final String FOURNISSEUR = "AndroidKeyStore";
    private static final String ALIAS = "ferpro_horloge_hmac";

    @Override
    public byte[] calculer(byte[] donnees) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(FOURNISSEUR);
            keyStore.load(null);
            if (!keyStore.containsAlias(ALIAS)) {
                KeyGenerator generateur = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_HMAC_SHA256, FOURNISSEUR);
                generateur.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build());
                generateur.generateKey();
            }
            SecretKey cle = (SecretKey) keyStore.getKey(ALIAS, null);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cle);
            return mac.doFinal(donnees);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }
}