package com.example.gkm_bar_fer.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class InstallationIdTest {

    @Test
    public void generer_produitUnFormatValide() {
        for (int i = 0; i < 1000; i++) {
            assertTrue(InstallationId.estValide(InstallationId.generer()));
        }
    }

    @Test
    public void generer_produitDesIdentifiantsUniques() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            ids.add(InstallationId.generer());
        }
        assertEquals(5000, ids.size());
    }

    @Test
    public void normaliser_accepteMinusculesEspacesEtSansTirets() {
        assertEquals("FP-A7K2-98MX-42PQ", InstallationId.normaliser(" fp a7k2 98mx 42pq "));
        assertEquals("FP-A7K2-98MX-42PQ", InstallationId.normaliser("FPA7K298MX42PQ"));
    }

    @Test
    public void normaliser_refuseLesIdentifiantsInvalides() {
        assertNull(InstallationId.normaliser(null));
        assertNull(InstallationId.normaliser("FP-A7K2-98MX-42P0")); // 0 interdit
        assertNull(InstallationId.normaliser("FP-A7K2-98MX-42PI")); // I interdit
        assertNull(InstallationId.normaliser("XX-A7K2-98MX-42PQ")); // mauvais préfixe
        assertNull(InstallationId.normaliser("FP-A7K2-98MX"));      // trop court
    }

    @Test
    public void estValide_refuseNull() {
        assertFalse(InstallationId.estValide(null));
    }
}