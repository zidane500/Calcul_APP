package com.example.gkm_bar_fer.scan;

/** Poids linéique de l'acier : sert à comparer ce qui est lu avec le poids imprimé sur le plan. */
public final class PoidsAcier {

    private PoidsAcier() {
    }

    /** kg par mètre pour un diamètre en mm (d² / 162,2 : 0,617 pour Ø10, 0,888 pour Ø12...). */
    public static double kgParMetre(int diametreMm) {
        return diametreMm * diametreMm / 162.2;
    }
}