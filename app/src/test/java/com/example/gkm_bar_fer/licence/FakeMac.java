package com.example.gkm_bar_fer.licence;

import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * "Keystore" de test : une clé HMAC en mémoire. Deux FakeMac avec des clés différentes
 * représentent deux téléphones différents.
 */
class FakeMac implements ClockGuard.Mac {

    private final byte[] cle;
    boolean indisponible = false;

    FakeMac(String secret) {
        this.cle = secret.getBytes();
    }

    @Override
    public byte[] calculer(byte[] donnees) throws GeneralSecurityException {
        if (indisponible) {
            throw new GeneralSecurityException("Keystore indisponible (test)");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(cle, "HmacSHA256"));
        return mac.doFinal(donnees);
    }
}