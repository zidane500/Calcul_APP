package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.security.SecureRandom;

public class LicenseUtilsTest {

    @Test
    public void base32_aller_retour() {
        SecureRandom alea = new SecureRandom();
        for (int taille = 0; taille < 120; taille++) {
            byte[] data = new byte[taille];
            alea.nextBytes(data);
            assertArrayEquals(data, Base32.decode(Base32.encode(data)));
        }
    }

    @Test
    public void base32_valeurConnue() {
        // Vecteur de test officiel RFC 4648 : "foobar" -> MZXW6YTBOI
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".getBytes()));
        assertArrayEquals("foobar".getBytes(), Base32.decode("mzxw6ytboi"));
    }

    @Test
    public void base32_ignoreEspacesTiretsEtRefuseLesCaracteresInvalides() {
        assertArrayEquals("foobar".getBytes(), Base32.decode("MZXW6Y-TB OI\n"));
        assertNull(Base32.decode("MZXW6Y*TBOI"));
        assertNull(Base32.decode("MZXW0YTBOI")); // 0 n'existe pas en Base32
    }

    @Test
    public void dates_valeursConnues() {
        assertEquals(LicenseDates.JOUR_EPOCH_2020, LicenseDates.jourEpoch(2020, 1, 1));
        assertEquals(20745, LicenseDates.jourEpoch(2026, 10, 19));
        assertEquals(21109, LicenseDates.jourEpoch(2027, 10, 18));
        assertEquals("19/10/2026", LicenseDates.formater(20745));
    }

    @Test
    public void dates_aller_retour_sur_plusieurs_annees() {
        for (int jour = 18262; jour < 18262 + 365 * 40; jour++) {
            int[] c = LicenseDates.civil(jour);
            assertEquals(jour, LicenseDates.jourEpoch(c[0], c[1], c[2]));
        }
    }

    @Test
    public void dates_ajouterMois() {
        int debut = LicenseDates.jourEpoch(2026, 10, 18);
        assertEquals(LicenseDates.jourEpoch(2027, 10, 18), LicenseDates.ajouterMois(debut, 12));
        assertEquals(LicenseDates.jourEpoch(2027, 1, 18), LicenseDates.ajouterMois(debut, 3));
        assertEquals(LicenseDates.jourEpoch(2026, 2, 28),
                LicenseDates.ajouterMois(LicenseDates.jourEpoch(2026, 1, 31), 1));
        assertEquals(LicenseDates.jourEpoch(2024, 2, 29),
                LicenseDates.ajouterMois(LicenseDates.jourEpoch(2024, 1, 31), 1));
        assertEquals(LicenseDates.jourEpoch(2028, 10, 18), LicenseDates.ajouterMois(debut, 24));
    }
}