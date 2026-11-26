package com.example.gkm_bar_fer.scan;

/**
 * Type d'élément reconnu sur un plan. Les codes sont ceux de {@code TypeElement} (écran de calcul),
 * pour pouvoir pré-remplir un calcul sans conversion. {@link #INCONNU} oblige l'utilisateur à choisir.
 */
public enum TypeElementScan {
    POTEAU("poteau"),
    PRE_POTEAU("pre_poteau"),
    SEMELLE("semelle"),
    POUTRE("poutre"),
    CHAINAGE("chainage"),
    DALLE("dalle"),
    INCONNU("");

    private final String code;

    TypeElementScan(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** Retrouve un type depuis son code ; {@link #INCONNU} si le code n'existe pas. */
    public static TypeElementScan depuisCode(String code) {
        for (TypeElementScan t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return INCONNU;
    }
}