package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import com.example.gkm_bar_fer.licence.LicenseVerifier.Resultat;

import org.junit.Test;

/**
 * Vérifie que la VRAIE chaîne fonctionne : licence signée par ton générateur PC
 * -> acceptée par le vérificateur de l'app avec la clé publique de LicenseConstants.
 */
public class RealKeyIntegrationTest {

    private static final String ID_DE_TEST = "FP-A7K2-98MX-42PQ";

    // Colle ici le code généré (1 an, début 18/10/2026, pour l'ID de test ci-dessus).
    private static final String CODE_GENERE = "AEAQAQ-JXJMZD-SOCNLA-2DEUCR-BG6AWK-RQIUBC-CAGALN-OVXWXU-YWLMWM-LTVXBX-LCHQNK-RNVEX4-55537V-6FGOZ4-AKK6H4-BCABSM-VXFFT2-M43WXB-YJQELG-6EIJ6P-3YMARA-6ZI7DM-PI3CAK-OFM6M6";

    @Test
    public void licenceDuGenerateur_estAccepteeParLApplication() {
        assumeFalse("Colle d'abord le code généré.", CODE_GENERE.startsWith("COLLE_ICI"));

        LicenseVerifier verifier = LicenseVerifier.integre();
        assertNotNull("PUBLIC_KEY_BASE32 est vide ou invalide dans LicenseConstants.", verifier);

        int jour = LicenseDates.jourEpoch(2026, 10, 19);
        assertEquals(Resultat.OK,
                verifier.verifier(CODE_GENERE, ID_DE_TEST, jour).getResultat());
        assertEquals(Resultat.APPAREIL_INCORRECT,
                verifier.verifier(CODE_GENERE, "FP-B3NR-74TW-56YZ", jour).getResultat());
        assertEquals(Resultat.EXPIREE,
                verifier.verifier(CODE_GENERE, ID_DE_TEST,
                        LicenseDates.jourEpoch(2027, 10, 19)).getResultat());
    }
}