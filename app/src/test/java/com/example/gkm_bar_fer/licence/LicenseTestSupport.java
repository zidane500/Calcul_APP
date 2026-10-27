package com.example.gkm_bar_fer.licence;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/**
 * Outils de test : génère une paire de clés TEMPORAIRE (jamais ta vraie clé) et
 * signe des licences comme le fera le générateur PC à l'étape 5.
 */
final class LicenseTestSupport {

    private LicenseTestSupport() {
    }

    static KeyPair nouvelleCle() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    static String signer(PrivateKey clePrivee, LicensePayload licence) throws Exception {
        return signerOctets(clePrivee, licence.toBytes());
    }

    static String signerOctets(PrivateKey clePrivee, byte[] contenu) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(clePrivee);
        signature.update(contenu);
        byte[] sig = signature.sign();

        byte[] tout = new byte[contenu.length + sig.length];
        System.arraycopy(contenu, 0, tout, 0, contenu.length);
        System.arraycopy(sig, 0, tout, contenu.length, sig.length);
        return Base32.grouper(Base32.encode(tout), LicenseConstants.TAILLE_GROUPE_CODE);
    }
}