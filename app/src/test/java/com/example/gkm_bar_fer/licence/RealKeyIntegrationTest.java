package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import com.example.gkm_bar_fer.licence.LicenseVerifier.Resultat;

import org.junit.Test;

/**
 * Vérifie que la VRAIE chaîne fonctionne : un code généré par ton générateur PC
 * est reconnu par l'application avec la clé publique de LicenseConstants.
 *
 * Le test marche avec N'IMPORTE QUEL code généré (toute durée, tout Installation ID) :
 * il lit lui-même l'ID et les dates dans le code, après avoir vérifié la signature.
 */
public class RealKeyIntegrationTest {

    // Colle ici un code généré par ton générateur (de préférence pour un ID de TEST).
    private static final String CODE_GENERE = "AEAQAT-SRKNLV-CNCQGZ-DFGS2D-BHAAWL-JQIQBC-AFEY4Y-ZGT53E-KCASXN-QHDG2J-VLWV3C-YND53B-RFYR77-KFBKS6-FKQGAI-QB3OQZ-FY2GSE-OD6G2J-47X6ZN-W5ITIZ-J5S4NR-KESDK7-YHPHCZ-N3ICQ";

    @Test
    public void licenceDuGenerateur_estAccepteeParLApplication() {
        assumeFalse("Colle d'abord un code généré.", CODE_GENERE.startsWith("AEAQAT-SRKNLV-CNCQGZ-DFGS2D-BHAAWL-JQIQBC-AFEY4Y-ZGT53E-KCASXN-QHDG2J-VLWV3C-YND53B-RFYR77-KFBKS6-FKQGAI-QB3OQZ-FY2GSE-OD6G2J-47X6ZN-W5ITIZ-J5S4NR-KESDK7-YHPHCZ-N3ICQ"));

        LicenseVerifier verifier = LicenseVerifier.integre();
        assertNotNull("PUBLIC_KEY_BASE32 est vide ou invalide dans LicenseConstants.", verifier);

        // 1) La signature est reconnue avec la vraie clé publique : le contenu est lisible.
        LicensePayload licence =
                verifier.verifier(CODE_GENERE, "FP-A7K2-98MX-42PQ", 0).getLicence();
        assertNotNull("Signature non reconnue : la clé publique de LicenseConstants n'est pas "
                + "celle de ton générateur, ou le code est incomplet.", licence);

        // 2) Avec l'ID et la date de début lus dans le code, la licence est acceptée.
        String id = licence.getInstallationId();
        int debut = licence.getJourDebut();
        assertEquals(Resultat.OK, verifier.verifier(CODE_GENERE, id, debut).getResultat());

        // 3) Un autre téléphone est refusé.
        String autreId = id.equals("FP-B3NR-74TW-56YZ") ? "FP-A7K2-98MX-42PQ" : "FP-B3NR-74TW-56YZ";
        assertEquals(Resultat.APPAREIL_INCORRECT,
                verifier.verifier(CODE_GENERE, autreId, debut).getResultat());

        // 4) Avant la date de début, la licence n'est pas encore valable.
        assertEquals(Resultat.PAS_ENCORE_VALIDE,
                verifier.verifier(CODE_GENERE, id, debut - 10).getResultat());

        // 5) Après la date de fin, la licence est expirée (sauf licence à vie).
        if (!licence.estAVie()) {
            assertEquals(Resultat.EXPIREE,
                    verifier.verifier(CODE_GENERE, id, licence.getJourFin() + 1).getResultat());
        }
    }
}