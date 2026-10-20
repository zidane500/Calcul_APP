#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Étape 3 de la traduction (français -> arabe) : RÉSULTATS DU CALCUL et textes de MainActivity.

À lancer UNE FOIS depuis le dossier du projet (celui qui contient "app") :
    python i18n_etape3.py        (ou : py i18n_etape3.py)

Ce que fait le script :
  1. vérifie que ton code correspond à ce qu'il attend ; sinon il s'arrête SANS rien modifier
  2. sauvegarde les fichiers qu'il va modifier dans "i18n3_sauvegarde_<date>"
  3. crée res/values/types.xml, res/values-ar/types.xml, res/values/calcul.xml, res/values-ar/calcul.xml
  4. remplace TypeElement.java (libellés des types : poteau, dalle... selon la langue)
  5. modifie MainActivity.java et CalculsClientActivity.java (textes, résultats, messages)
  6. règle le sens d'écriture des zones de résultat pour l'arabe (textDirection)

Les exports PDF et Excel restent en français pour l'instant (étape suivante).
"""

import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

# -*- coding: utf-8 -*-
# Données de traduction de l'étape 3 (résultats du calcul)

TYPES = [
    dict(cle="poteau", ar_two_gen="عمودين", fr_sing="poteau", fr_plur="poteaux", fr_def="le poteau", fr_un_autre="un autre", fr_ce="ce",
         ar_sing="عمود", ar_def="العمود", ar_plur_def="الأعمدة", ar_dem="هذا", ar_autre="آخر",
         ar_plur=dict(zero="%d عمود", one="عمود واحد", two="عمودان", few="%d أعمدة", many="%d عمودا", other="%d عمود")),
    dict(cle="dalle", ar_two_gen="بلاطتين", fr_sing="dalle", fr_plur="dalles", fr_def="la dalle", fr_un_autre="une autre", fr_ce="cette",
         ar_sing="بلاطة", ar_def="البلاطة", ar_plur_def="البلاطات", ar_dem="هذه", ar_autre="أخرى",
         ar_plur=dict(zero="%d بلاطة", one="بلاطة واحدة", two="بلاطتان", few="%d بلاطات", many="%d بلاطة", other="%d بلاطة")),
    dict(cle="semelle", ar_two_gen="أساسين", fr_sing="semelle", fr_plur="semelles", fr_def="la semelle", fr_un_autre="une autre", fr_ce="cette",
         ar_sing="أساس", ar_def="الأساس", ar_plur_def="الأسس", ar_dem="هذا", ar_autre="آخر",
         ar_plur=dict(zero="%d أساس", one="أساس واحد", two="أساسان", few="%d أسس", many="%d أساسا", other="%d أساس")),
    dict(cle="poutre", ar_two_gen="رافدتين", fr_sing="poutre", fr_plur="poutres", fr_def="la poutre", fr_un_autre="une autre", fr_ce="cette",
         ar_sing="رافدة", ar_def="الرافدة", ar_plur_def="الروافد", ar_dem="هذه", ar_autre="أخرى",
         ar_plur=dict(zero="%d رافدة", one="رافدة واحدة", two="رافدتان", few="%d روافد", many="%d رافدة", other="%d رافدة")),
    dict(cle="pre_poteau", ar_two_gen="عمودين مسبقي الصنع", fr_sing="pré-poteau", fr_plur="pré-poteaux", fr_def="le pré-poteau", fr_un_autre="un autre", fr_ce="ce",
         ar_sing="عمود مسبق الصنع", ar_def="العمود المسبق الصنع", ar_plur_def="الأعمدة المسبقة الصنع", ar_dem="هذا", ar_autre="آخر",
         ar_plur=dict(zero="%d عمود مسبق الصنع", one="عمود مسبق الصنع واحد", two="عمودان مسبقا الصنع",
                      few="%d أعمدة مسبقة الصنع", many="%d عمودا مسبق الصنع", other="%d عمود مسبق الصنع")),
    dict(cle="escalier", ar_two_gen="درجين", fr_sing="escalier", fr_plur="escaliers", fr_def="l'escalier", fr_un_autre="un autre", fr_ce="cet",
         ar_sing="درج", ar_def="الدرج", ar_plur_def="الأدراج", ar_dem="هذا", ar_autre="آخر",
         ar_plur=dict(zero="%d درج", one="درج واحد", two="درجان", few="%d أدراج", many="%d درجا", other="%d درج")),
]

# clé : (français, arabe)
STRINGS = {
    # --- en-tête et besoins
    "entete_groupe": ("%1$s de %2$s m (%3$s)", "%1$s بطول %2$s م (%3$s)"),
    "erreur_au_moins_une_barre": ("Veuillez saisir au moins un type de barre avec une quantité positive.\n\n",
                                  "يرجى إدخال نوع واحد على الأقل من القضبان بكمية موجبة.\n\n"),
    "ligne_besoin": ("  • %1$s: %2$s/%3$s → %4$s au total\n", "  • %1$s : %2$s لكل %3$s ← %4$s في المجموع\n"),
    "titre_optimise_chantier": ("=== Optimisé sur l'ensemble du chantier ===", "=== محسّن على مستوى الورشة كاملة ==="),
    # --- validation
    "erreur_remplir_champs": ("%1$s #%2$d: veuillez remplir le nombre de %3$s et la longueur",
                              "%1$s #%2$d : يرجى ملء عدد %3$s والطول"),
    "erreur_valeurs_positives": ("%1$s #%2$d: les valeurs doivent être positives", "%1$s #%2$d : يجب أن تكون القيم موجبة"),
    "erreur_nombres_valides": ("%1$s #%2$d: veuillez entrer des nombres valides", "%1$s #%2$d : يرجى إدخال أرقام صحيحة"),
    "barre_deja_selectionnee": ("%1$s est déjà sélectionné", "%1$s محدد مسبقا"),
    # --- résumé par type de barre
    "oui": ("Oui", "نعم"),
    "non": ("Non", "لا"),
    "resume_titre_type": ("• %1$s:\n", "• %1$s :\n"),
    "resume_barres_acheter": ("  - Barres à acheter: %1$d\n", "  - قضبان للشراء : %1$d\n"),
    "resume_dechets_aucun": ("  - Déchets: Aucun", "  - الفضلات : لا شيء"),
    "resume_dechets_nombre": ("  - Déchets: %1$s\n", "  - الفضلات : %1$s\n"),
    "resume_chute_unique": ("    * Chute: %1$s m", "    * فضلة : %1$s م"),
    "resume_optimise": ("\n  - Optimisé: %1$s", "\n  - محسّن : %1$s"),
    # --- détail des barres
    "barre_numero": ("Barre %1$d : %2$s", "القضيب %1$d : %2$s"),
    "detail_barre": ("%1$s avec %2$s%3$s", "%1$s مع %2$s%3$s"),
    "chute_aucune": ("0 chutes", "عدم وجود فضلات"),
    "chute_une": ("chute %1$s m", "فضلة %1$s م"),
    "liaison_et": (" et ", " و"),
    "marqueur_stock": (" (depuis le stock, chute initiale de %1$s m)", " (من المخزون، الفضلة الأصلية %1$s م)"),
    "resume_barre_une": ("1 Barre : %1$s", "قضيب واحد : %1$s"),
    "segment_info": (" (segment %1$s)", " (الجزء %1$s)"),
    "coupes_pour_plusieurs": ("%1$s de %2$s m%3$s pour les %4$s de %5$s m", "%1$s بطول %2$s م%3$s لأجل %4$s بطول %5$s م"),
    "coupes_pour_un": ("%1$s de %2$s m%3$s pour %4$s de %5$s m", "%1$s بطول %2$s م%3$s لأجل %4$s بطول %5$s م"),
    # --- boutons du bloc résultat
    "bouton_annuler_stock": ("Annuler utilisation du stock", "إلغاء استعمال المخزون"),
    "btn_afficher_detail": ("Afficher détail", "عرض التفاصيل"),
    "btn_masquer_detail": ("Masquer détail", "إخفاء التفاصيل"),
    "btn_masquer_resume": ("Masquer résumé", "إخفاء الملخص"),
    "btn_retirer_chutes": ("Retirer chutes envoyées", "سحب الفضلات المرسلة"),
    # --- messages liés au stock
    "stock_confirmation_impossible": (
        "Confirmation impossible : vérifiez le stock. L'opération peut aussi être déjà enregistrée ou la sauvegarde avoir échoué.",
        "تعذّر التأكيد : تحقق من المخزون. وقد تكون العملية مسجلة مسبقا أو فشل الحفظ."),
    "stock_utilisation_confirmee": ("Utilisation du stock confirmée pour %1$s.", "تم تأكيد استعمال المخزون لـ %1$s."),
    "stock_annulation_impossible": (
        "Annulation impossible : le stock a pu être modifié depuis la confirmation, ou la sauvegarde a échoué.",
        "تعذّر الإلغاء : ربما تم تعديل المخزون منذ التأكيد، أو فشل الحفظ."),
    "stock_utilisation_annulee": ("Utilisation du stock annulée pour %1$s.", "تم إلغاء استعمال المخزون لـ %1$s."),
    "dechets_ajoutes": ("Déchets de %1$s ajoutés au stock.", "تمت إضافة فضلات %1$s إلى المخزون."),
    "dechets_retires": ("Déchets de %1$s retirés du stock.", "تم سحب فضلات %1$s من المخزون."),
    # --- exports
    "calculer_d_abord": ("Veuillez d'abord calculer les résultats.", "يرجى حساب النتائج أولا."),
    "erreur_pdf": ("Erreur lors de la génération du PDF : %1$s", "خطأ أثناء إنشاء ملف PDF : %1$s"),
    "erreur_excel": ("Erreur lors de la génération du fichier Excel : %1$s", "خطأ أثناء إنشاء ملف Excel : %1$s"),
    "aucune_app_pdf": ("Aucune application pour ouvrir le PDF. Fichier enregistré : %1$s",
                       "لا يوجد تطبيق لفتح ملف PDF. تم حفظ الملف : %1$s"),
    "aucune_app_excel": ("Aucune application pour ouvrir le fichier Excel. Fichier enregistré : %1$s",
                         "لا يوجد تطبيق لفتح ملف Excel. تم حفظ الملف : %1$s"),
    "ouvrir_pdf_avec": ("Ouvrir le PDF avec", "فتح ملف PDF باستخدام"),
    "ouvrir_excel_avec": ("Ouvrir le fichier Excel avec", "فتح ملف Excel باستخدام"),
    # --- enregistrement pour un client
    "btn_enregistrer_modifs": ("Enregistrer les modifications", "حفظ التعديلات"),
    "btn_enregistrer_pour": ("Enregistrer pour %1$s", "حفظ لـ %1$s"),
    "calculer_avant_enregistrer": ("Calculez d'abord avant d'enregistrer.", "احسب أولا قبل الحفظ."),
    "nom_calcul_defaut": ("Calcul du %1$s", "حساب بتاريخ %1$s"),
    "dialog_titre_enregistrer": ("Enregistrer le calcul", "حفظ الحساب"),
    "dialog_ignorer": ("Ignorer", "تجاهل"),
    "dialog_enregistrer": ("Enregistrer", "حفظ"),
    "nom_calcul_obligatoire": ("Le nom du calcul est obligatoire", "اسم الحساب إلزامي"),
    "enregistrement_echoue": ("L'enregistrement a échoué.", "فشل الحفظ."),
    "modifs_enregistrees": ("Modifications enregistrées.", "تم حفظ التعديلات."),
    "calcul_enregistre": ("Calcul enregistré.", "تم حفظ الحساب."),
    "impossible_enregistrer": ("Impossible d'enregistrer ce calcul.", "تعذّر حفظ هذا الحساب."),
    "calcul_introuvable": ("Calcul introuvable.", "الحساب غير موجود."),
    "calcul_illisible": ("Ce calcul enregistré est illisible.", "هذا الحساب المحفوظ غير قابل للقراءة."),
    "calcul_modifie_le": ("Modifié le %1$s", "تم التعديل في %1$s"),
    "calcul_enregistre_le": ("Enregistré le %1$s", "تم الحفظ في %1$s"),
}

FR_ARCAT = None  # (voir PLURALS)

# clé : ({quantité: français}, {quantité: arabe})
PLURALS = {
    "nb_coupes": (dict(one="%d coupe", other="%d coupes"),
                  dict(zero="%d قطعة", one="قطعة واحدة", two="قطعتان", few="%d قطع", many="%d قطعة", other="%d قطعة")),
    "nb_chutes": (dict(one="%d chute", other="%d chutes"),
                  dict(zero="لا فضلات", one="فضلة واحدة", two="فضلتان", few="%d فضلات", many="%d فضلة", other="%d فضلة")),
    "chutes_de": (dict(one="%1$d chute de %2$s m", other="%1$d chutes de %2$s m"),
                  dict(zero="%1$d فضلة بطول %2$s م", one="فضلة واحدة بطول %2$s م", two="فضلتين بطول %2$s م",
                       few="%1$d فضلات بطول %2$s م", many="%1$d فضلة بطول %2$s م", other="%1$d فضلة بطول %2$s م")),
    "resume_chutes_multiples": (dict(one="    * %1$d chute: %2$s m", other="    * %1$d chutes: %2$s m"),
                                dict(zero="    * %1$d فضلة : %2$s م", one="    * فضلة واحدة : %2$s م",
                                     two="    * فضلتان : %2$s م", few="    * %1$d فضلات : %2$s م",
                                     many="    * %1$d فضلة : %2$s م", other="    * %1$d فضلة : %2$s م")),
    "resume_barres_identiques": (dict(one="%1$d Barre : pour chaque Barre, %2$s", other="%1$d Barres : pour chaque Barre, %2$s"),
                                 dict(zero="%1$d قضيب : لكل قضيب، %2$s", one="قضيب واحد : لكل قضيب، %2$s",
                                      two="قضيبان : لكل قضيب، %2$s", few="%1$d قضبان : لكل قضيب، %2$s",
                                      many="%1$d قضيبا : لكل قضيب، %2$s", other="%1$d قضيب : لكل قضيب، %2$s")),
}

# =====================================================================================
#  Génération des fichiers de ressources
# =====================================================================================

def valeur_xml(texte):
    """Échappe un texte pour une ressource Android (guillemets si espaces aux extrémités)."""
    t = texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    t = t.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    if texte != texte.strip():
        t = '"' + t + '"'
    return t


def majuscule(t):
    return t[:1].upper() + t[1:]


def ligne_string(nom, texte):
    return '    <string name="%s">%s</string>' % (nom, valeur_xml(texte))


def bloc_plurals(nom, items):
    ordre = ["zero", "one", "two", "few", "many", "other"]
    lignes = ['    <plurals name="%s">' % nom]
    for q in ordre:
        if q in items:
            lignes.append('        <item quantity="%s">%s</item>' % (q, valeur_xml(items[q])))
    lignes.append("    </plurals>")
    return "\n".join(lignes)


def generer_types(lang):
    l = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>",
         "    <!-- Noms des types d'éléments (générés par i18n_etape3.py) -->"]
    for t in TYPES:
        c = t["cle"]
        if lang == "fr":
            sing, plur = t["fr_sing"], t["fr_plur"]
            valeurs = [
                ("singulier", sing),
                ("defini", t["fr_def"]),
                ("pluriel_defini", plur),
                ("infos", "Informations des " + majuscule(plur)),
                ("nombre", "Nombre de " + plur),
                ("longueur", "Longueur de " + sing),
                ("ajouter", "Ajouter %s %s" % (t["fr_un_autre"], sing)),
                ("supprimer", "Supprimer %s %s" % (t["fr_ce"], sing)),
                ("titre_calcul", "Calcul %s - %%1$s" % sing),
            ]
            plurals = dict(one="%d " + sing, other="%d " + plur)
        else:
            valeurs = [
                ("singulier", t["ar_sing"]),
                ("defini", t["ar_def"]),
                ("pluriel_defini", t["ar_plur_def"]),
                ("infos", "معلومات " + t["ar_plur_def"]),
                ("nombre", "عدد " + t["ar_plur_def"]),
                ("longueur", "طول " + t["ar_def"]),
                ("ajouter", "إضافة %s %s" % (t["ar_sing"], t["ar_autre"])),
                ("supprimer", "حذف %s %s" % (t["ar_dem"], t["ar_def"])),
                ("titre_calcul", "حساب %s - %%1$s" % t["ar_def"]),
            ]
            plurals = t["ar_plur"]
        for suffixe, texte in valeurs:
            l.append(ligne_string("type_%s_%s" % (c, suffixe), texte))
        l.append(bloc_plurals("nb_" + c, plurals))
        if lang == "fr":
            plurals_gen = plurals
        else:
            plurals_gen = dict(plurals)
            plurals_gen["two"] = t["ar_two_gen"]
        l.append(bloc_plurals("nbg_" + c, plurals_gen))   # forme après une préposition (génitif)
        l.append("")
    l.append("</resources>")
    return "\n".join(l) + "\n"


def generer_calcul(lang):
    i = 0 if lang == "fr" else 1
    l = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>",
         "    <!-- Textes des résultats du calcul (générés par i18n_etape3.py) -->"]
    for nom, textes in STRINGS.items():
        l.append(ligne_string(nom, textes[i]))
    l.append("")
    for nom, textes in PLURALS.items():
        l.append(bloc_plurals(nom, textes[i]))
    l.append("</resources>")
    return "\n".join(l) + "\n"


def noms_definis():
    noms = set(STRINGS) | set(PLURALS)
    for t in TYPES:
        c = t["cle"]
        noms |= {"type_%s_%s" % (c, s) for s in
                 ("singulier", "defini", "pluriel_defini", "infos", "nombre", "longueur",
                  "ajouter", "supprimer", "titre_calcul")}
        noms.add("nb_" + c)
        noms.add("nbg_" + c)
    return noms


# =====================================================================================
#  TypeElement.java (nouvelle version : libellés selon la langue)
# =====================================================================================

def generer_type_element():
    entrees = []
    legacy = {
        "poteau": ("poteau", "poteaux", "false"), "dalle": ("dalle", "dalles", "true"),
        "semelle": ("semelle", "semelles", "true"), "poutre": ("poutre", "poutres", "true"),
        "pre_poteau": ("pré-poteau", "pré-poteaux", "false"), "escalier": ("escalier", "escaliers", "false"),
    }
    for t in TYPES:
        c = t["cle"]
        sing, plur, fem = legacy[c]
        code = c if c != "pre_poteau" else "pre_poteau"
        entrees.append(
            '    %s("%s", "%s", %s, "%s",\n'
            '            R.string.type_%s_singulier, R.string.type_%s_defini, R.string.type_%s_pluriel_defini,\n'
            '            R.string.type_%s_infos, R.string.type_%s_nombre, R.string.type_%s_longueur,\n'
            '            R.string.type_%s_ajouter, R.string.type_%s_supprimer, R.string.type_%s_titre_calcul,\n'
            '            R.plurals.nb_%s, R.plurals.nbg_%s)'
            % ((c.upper(), sing, plur, fem, code) + (c,) * 11))
    corps = ",\n".join(entrees) + ";\n"
    return TYPE_ELEMENT_JAVA.replace("@@ENTREES@@", corps)


TYPE_ELEMENT_JAVA = r'''package com.example.gkm_bar_fer;

import android.content.Context;

/**
 * Les types d'éléments de construction pour lesquels on calcule les barres de fer.
 *
 * Les libellés viennent des fichiers de ressources (res/values/types.xml et
 * res/values-ar/types.xml) : ils suivent donc la langue de l'application. Les anciens accesseurs
 * sans Context (getSingulier()...) restent disponibles et renvoient toujours le français.
 */
public enum TypeElement {

@@ENTREES@@

    private final String singulier;
    private final String pluriel;
    private final boolean feminin;
    private final String code;

    private final int resSingulier;
    private final int resDefini;
    private final int resPlurielDefini;
    private final int resInfos;
    private final int resNombre;
    private final int resLongueur;
    private final int resAjouter;
    private final int resSupprimer;
    private final int resTitreCalcul;
    private final int resNombreElements;   // plurals : "%d poteau(x)"
    private final int resNombreGenitif;    // idem, forme après une préposition (arabe : duel au génitif)

    TypeElement(String singulier, String pluriel, boolean feminin, String code,
                int resSingulier, int resDefini, int resPlurielDefini,
                int resInfos, int resNombre, int resLongueur,
                int resAjouter, int resSupprimer, int resTitreCalcul,
                int resNombreElements, int resNombreGenitif) {
        this.singulier = singulier;
        this.pluriel = pluriel;
        this.feminin = feminin;
        this.code = code;
        this.resSingulier = resSingulier;
        this.resDefini = resDefini;
        this.resPlurielDefini = resPlurielDefini;
        this.resInfos = resInfos;
        this.resNombre = resNombre;
        this.resLongueur = resLongueur;
        this.resAjouter = resAjouter;
        this.resSupprimer = resSupprimer;
        this.resTitreCalcul = resTitreCalcul;
        this.resNombreElements = resNombreElements;
        this.resNombreGenitif = resNombreGenitif;
    }

    /** Code stocké dans les Intent et dans les calculs enregistrés (sans accent ni tiret). */
    public String getCode() {
        return code;
    }

    /** Retrouve un type à partir de son code. Par défaut : POTEAU. */
    public static TypeElement depuisCode(String code) {
        if (code != null) {
            for (TypeElement type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
        }
        return POTEAU;
    }

    // ------------------------------------------------------------------
    // Libellés dans la langue du Context (français ou arabe)
    // ------------------------------------------------------------------

    /** ex : poteau / عمود */
    public String singulier(Context c) {
        return c.getString(resSingulier);
    }

    public String singulierMajuscule(Context c) {
        return majuscule(singulier(c));
    }

    /** ex : le poteau / العمود */
    public String defini(Context c) {
        return c.getString(resDefini);
    }

    /** ex : poteaux / الأعمدة */
    public String plurielDefini(Context c) {
        return c.getString(resPlurielDefini);
    }

    public String plurielDefiniMajuscule(Context c) {
        return majuscule(plurielDefini(c));
    }

    /** ex : Informations des Poteaux */
    public String infos(Context c) {
        return c.getString(resInfos);
    }

    /** ex : Nombre de poteaux */
    public String nombre(Context c) {
        return c.getString(resNombre);
    }

    /** ex : Longueur de poteau */
    public String longueur(Context c) {
        return c.getString(resLongueur);
    }

    /** ex : Ajouter un autre poteau */
    public String ajouter(Context c) {
        return c.getString(resAjouter);
    }

    /** ex : Supprimer ce poteau */
    public String supprimer(Context c) {
        return c.getString(resSupprimer);
    }

    /** ex : Calcul poteau - Hama */
    public String titreCalcul(Context c, String nomClient) {
        return c.getString(resTitreCalcul, nomClient);
    }

    /** ex : "5 poteaux" (en arabe : "5 أعمدة", "عمودان", "11 عمودا"...) */
    public String compte(Context c, int nombre) {
        return c.getResources().getQuantityString(resNombreElements, nombre, nombre);
    }

    /** Comme compte(), pour une phrase où le nom suit une préposition ("pour 2 poutres" / "لأجل رافدتين"). */
    public String compteGenitif(Context c, int nombre) {
        return c.getResources().getQuantityString(resNombreGenitif, nombre, nombre);
    }

    // ------------------------------------------------------------------
    // Anciens accesseurs : toujours en français
    // ------------------------------------------------------------------

    /** ex : poteau */
    public String getSingulier() {
        return singulier;
    }

    /** ex : poteaux */
    public String getPluriel() {
        return pluriel;
    }

    /** ex : Poteau */
    public String getSingulierMajuscule() {
        return majuscule(singulier);
    }

    /** ex : Poteaux */
    public String getPlurielMajuscule() {
        return majuscule(pluriel);
    }

    /** ex : "un autre" / "une autre" */
    public String getUnAutre() {
        return feminin ? "une autre" : "un autre";
    }

    /** ex : "ce" / "cette" */
    public String getCe() {
        return feminin ? "cette" : "ce";
    }

    /** ex : "le poteau", "la dalle", "l'escalier" */
    public String getLeSingulier() {
        if (this == ESCALIER) {
            return "l'" + singulier;
        }
        return (feminin ? "la " : "le ") + singulier;
    }

    private static String majuscule(String texte) {
        return texte.substring(0, 1).toUpperCase() + texte.substring(1);
    }
}
'''

# =====================================================================================
#  Modifications du code Java et des layouts
# =====================================================================================

class Patcheur:
    def __init__(self, texte, nom):
        self.t = texte
        self.nom = nom
        self.erreurs = []

    def rep(self, ancien, nouveau, tous=False, libelle=None):
        if ancien not in self.t:
            self.erreurs.append((self.nom, libelle or ancien.strip()[:80]))
            return
        self.t = self.t.replace(ancien, nouveau) if tous else self.t.replace(ancien, nouveau, 1)

    def rep_re(self, motif, nouveau, libelle=None, tous=False):
        if not re.search(motif, self.t, re.S):
            self.erreurs.append((self.nom, libelle or motif[:80]))
            return
        self.t = re.sub(motif, lambda _m: nouveau, self.t, count=0 if tous else 1, flags=re.S)

    def rep_entre(self, debut, fin, nouveau, libelle=None, inclure_fin=False):
        i = self.t.find(debut)
        if i < 0:
            self.erreurs.append((self.nom, libelle or debut.strip()[:80]))
            return
        j = self.t.find(fin, i)
        if j < 0:
            self.erreurs.append((self.nom, (libelle or debut.strip()[:60]) + " (fin introuvable)"))
            return
        if inclure_fin:
            j += len(fin)
        self.t = self.t[:i] + nouveau + self.t[j:]


NOUVEAUX_FORMATTEURS = r'''    /**
     * Regroupe et met en forme les coupes d'une barre donnée, dans la langue du Context, ex :
     * "2 coupes de 2,00 m pour le poteau de 2,00 m + 1 coupe de 10,00 m pour les 3 poteaux de 10,00 m"
     */
    private String formatterDescriptionCoupes(BarreUtilisee barre, Context ctx) {
        LinkedHashMap<String, Integer> comptage = new LinkedHashMap<>();
        LinkedHashMap<String, CoupeRequise> exemples = new LinkedHashMap<>();
        for (CoupeRequise c : barre.coupes) {
            String cle = c.longueur + "|" + c.longueurPoteau + "|" + c.nombrePoteaux + "|" + c.segmentInfo;
            comptage.put(cle, comptage.getOrDefault(cle, 0) + 1);
            exemples.putIfAbsent(cle, c);
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : comptage.entrySet()) {
            CoupeRequise c = exemples.get(e.getKey());
            int n = e.getValue();
            String nbCoupes = ctx.getResources().getQuantityString(R.plurals.nb_coupes, n, n);
            String longueurCoupeTxt = String.format(Locale.FRANCE, "%.2f", c.longueur);
            String longueurPoteauTxt = String.format(Locale.FRANCE, "%.2f", c.longueurPoteau);
            String segmentTxt = c.segmentInfo != null
                    ? ctx.getString(R.string.segment_info, extraireSegment(c.segmentInfo))
                    : "";

            if (c.nombrePoteaux > 1) {
                parts.add(ctx.getString(R.string.coupes_pour_plusieurs, nbCoupes, longueurCoupeTxt,
                        segmentTxt, typeElement.compteGenitif(ctx, c.nombrePoteaux), longueurPoteauTxt));
            } else {
                parts.add(ctx.getString(R.string.coupes_pour_un, nbCoupes, longueurCoupeTxt,
                        segmentTxt, typeElement.defini(ctx), longueurPoteauTxt));
            }
        }
        return TextUtils.join(" + ", parts);
    }

    private String marqueurStock(BarreUtilisee barre, Context ctx) {
        if (!barre.estDuStock) {
            return "";
        }

        return ctx.getString(R.string.marqueur_stock,
                String.format(Locale.FRANCE, "%.2f", barre.longueurOrigineStock));
    }

    private String formatterDetailBarre(BarreUtilisee barre, Context ctx) {
        List<Double> chutes = barre.resteDisponible > 1e-9
                ? Collections.singletonList(barre.resteDisponible)
                : Collections.<Double>emptyList();

        return ctx.getString(R.string.detail_barre,
                formatterDescriptionCoupes(barre, ctx),
                formatterChute(chutes, ctx),
                marqueurStock(barre, ctx));
    }

    /**
     * Regroupe les barres identiques (même composition + même chute) en une seule ligne
     * récapitulative, ex : "3 Barres : pour chaque Barre, 1 coupe de 9,00 m pour les 5
     * poteaux de 9,00 m avec chute 3,00 m".
     */
    private String formatterResumeBarres(List<BarreUtilisee> detailBarres, Context ctx) {
        LinkedHashMap<String, Integer> comptage = new LinkedHashMap<>();
        for (BarreUtilisee barre : detailBarres) {
            String description = formatterDetailBarre(barre, ctx);
            comptage.put(description, comptage.getOrDefault(description, 0) + 1);
        }

        List<String> lignes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : comptage.entrySet()) {
            int nombre = entry.getValue();
            String description = entry.getKey();
            if (nombre == 1) {
                lignes.add(ctx.getString(R.string.resume_barre_une, description));
            } else {
                lignes.add(ctx.getResources().getQuantityString(
                        R.plurals.resume_barres_identiques, nombre, nombre, description));
            }
        }
        return TextUtils.join("\n", lignes);
    }

    private String formatterChute(List<Double> chutes, Context ctx) {
        if (chutes.isEmpty()) {
            return ctx.getString(R.string.chute_aucune);
        }
        Map<Double, Integer> comptageChutes = new LinkedHashMap<>();
        for (Double d : chutes) {
            comptageChutes.put(d, comptageChutes.getOrDefault(d, 0) + 1);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Double, Integer> e : comptageChutes.entrySet()) {
            String longueurTxt = String.format(Locale.FRANCE, "%.2f", e.getKey());
            int n = e.getValue();
            if (n == 1) {
                parts.add(ctx.getString(R.string.chute_une, longueurTxt));
            } else {
                parts.add(ctx.getResources().getQuantityString(R.plurals.chutes_de, n, n, longueurTxt));
            }
        }
        return TextUtils.join(ctx.getString(R.string.liaison_et), parts);
    }

'''

NOUVEAU_RESUME = r'''        StringBuilder resume = new StringBuilder();
        resume.append(getString(R.string.resume_titre_type, typeBarre));
        resume.append(getString(R.string.resume_barres_acheter, resultat.barresNecessaires));

        if (resultat.listeDechets.isEmpty()) {
            resume.append(getString(R.string.resume_dechets_aucun));
        } else {
            int nbDechets = resultat.listeDechets.size();
            resume.append(getString(R.string.resume_dechets_nombre,
                    getResources().getQuantityString(R.plurals.nb_chutes, nbDechets, nbDechets)));

            Map<Double, Integer> dechetsParLongueur = new HashMap<>();
            for (Double dechet : resultat.listeDechets) {
                dechetsParLongueur.put(dechet, dechetsParLongueur.getOrDefault(dechet, 0) + 1);
            }

            List<String> lignesDechets = new ArrayList<>();
            for (Map.Entry<Double, Integer> entryDechet : dechetsParLongueur.entrySet()) {
                String longueurTxt = String.format(Locale.FRANCE, "%.2f", entryDechet.getKey());
                int n = entryDechet.getValue();
                if (n == 1) {
                    lignesDechets.add(getString(R.string.resume_chute_unique, longueurTxt));
                } else {
                    lignesDechets.add(getResources().getQuantityString(
                            R.plurals.resume_chutes_multiples, n, n, longueurTxt));
                }
            }
            resume.append(TextUtils.join("\n", lignesDechets));
            resume.append("\n");
        }
        resume.append(getString(R.string.resume_optimise,
                getString(resultat.utilisationOptimisee ? R.string.oui : R.string.non)));
        tvResumeBarre.setText(resume.toString());'''

NOUVEAUX_UTILITAIRES = r'''    /** Contexte en français : sert aux PDF et Excel, qui restent en français pour l'instant. */
    private Context contexteFrancais() {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.FRENCH);
        return createConfigurationContext(configuration);
    }

    /** "segment 1/2" (anciens calculs) ou "1/2" -> "1/2". */
    private static String extraireSegment(String info) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+/\\d+").matcher(info);
        return m.find() ? m.group() : info;
    }

    /**
     * Reconstruit, dans la langue actuelle, le texte des besoins ("=== Poteaux de ... ===")
     * à partir des données du calcul. Sert à l'ouverture d'un calcul enregistré : le texte
     * n'est plus figé dans la langue où il a été enregistré.
     */
    private String construireTexteBesoins() {
        StringBuilder texte = new StringBuilder();
        String dernierGroupe = null;

        for (String[] ligne : derniersGroupesPoteaux) {
            if (ligne.length < 5) {
                continue;
            }
            try {
                int nombre = Integer.parseInt(ligne[1]);
                int parElement = Integer.parseInt(ligne[3]);
                int total = Integer.parseInt(ligne[4]);

                String cleGroupe = ligne[0] + "|" + ligne[1];
                if (!cleGroupe.equals(dernierGroupe)) {
                    if (dernierGroupe != null) {
                        texte.append("\n");
                    }
                    texte.append("=== ")
                            .append(getString(R.string.entete_groupe,
                                    typeElement.plurielDefiniMajuscule(this),
                                    ligne[0],
                                    typeElement.compte(this, nombre)))
                            .append(" ===\n");
                    dernierGroupe = cleGroupe;
                }

                texte.append(getString(R.string.ligne_besoin,
                        ligne[2],
                        getResources().getQuantityString(R.plurals.nb_coupes, parElement, parElement),
                        typeElement.singulier(this),
                        getResources().getQuantityString(R.plurals.nb_coupes, total, total)));
            } catch (NumberFormatException ignore) {
                // ligne illisible : ignorée
            }
        }

        if (texte.length() > 0) {
            texte.append("\n");
        }
        texte.append(getString(R.string.titre_optimise_chantier));
        return texte.toString();
    }

'''


def patcher_main(src):
    p = Patcheur(src, "MainActivity.java")

    # --- import
    p.rep("import android.content.res.ColorStateList;\n",
          "import android.content.res.ColorStateList;\nimport android.content.res.Configuration;\n",
          libelle="import ColorStateList")

    if "import android.content.Context;\n" not in p.t:
        p.rep("import android.content.Intent;\n", "import android.content.Context;\nimport android.content.Intent;\n",
              libelle="import Intent")

    # --- libellés du type d'élément
    p.rep_re(r'btnAjouterPoteau\.setText\(\s*"Ajouter " \+ typeElement\.getUnAutre\(\) \+ " " \+ typeElement\.getSingulier\(\)\);',
             "btnAjouterPoteau.setText(typeElement.ajouter(this));", "bouton Ajouter un autre ...")
    p.rep('tvTitreGroupe.setText("Informations des " + typeElement.getPlurielMajuscule());',
          "tvTitreGroupe.setText(typeElement.infos(this));")
    p.rep('layoutNombre.setHint("Nombre de " + typeElement.getPluriel());',
          "layoutNombre.setHint(typeElement.nombre(this));")
    p.rep('layoutLongueur.setHint("Longueur de " + typeElement.getSingulier());',
          "layoutLongueur.setHint(typeElement.longueur(this));")
    p.rep_re(r'btnSupprimerGroupe\.setContentDescription\(\s*"Supprimer " \+ typeElement\.getCe\(\) \+ " " \+ typeElement\.getSingulier\(\)\);',
             "btnSupprimerGroupe.setContentDescription(typeElement.supprimer(this));", "description Supprimer")

    # --- ajout d'une barre
    p.rep('Toast.makeText(this, typeBarre + " est déjà sélectionné", Toast.LENGTH_SHORT).show();',
          "Toast.makeText(this, getString(R.string.barre_deja_selectionnee, typeBarre), Toast.LENGTH_SHORT).show();")

    # --- texte des besoins
    p.rep_re(r'String libellePoteaux = String\.format\(Locale\.FRANCE, "%s de %\.2f m \(%d %s\)",\s*'
             r'typeElement\.getPlurielMajuscule\(\), longueurPoteaux, nombrePoteaux,\s*'
             r'nombrePoteaux > 1 \? typeElement\.getPluriel\(\) : typeElement\.getSingulier\(\)\);',
             'String libellePoteaux = getString(R.string.entete_groupe,\n'
             '                    typeElement.plurielDefiniMajuscule(this),\n'
             '                    String.format(Locale.FRANCE, "%.2f", longueurPoteaux),\n'
             '                    typeElement.compte(this, nombrePoteaux));',
             "en-tête du groupe (libellePoteaux)")
    p.rep(r'detailPoteaux.append("Veuillez saisir au moins un type de barre avec une quantité positive.\n\n");',
          "detailPoteaux.append(getString(R.string.erreur_au_moins_une_barre));")
    p.rep_re(r'detailPoteaux\.append\("  • "\)\.append\(typeBarre\)\.append\(": "\)\s*'
             r'\.append\(nombreParPoteau\)\.append\(" coupes/"\)\.append\(typeElement\.getSingulier\(\)\)\.append\(" → "\)\s*'
             r'\.append\(totalCoupesNecessaires\)\.append\(" coupes au total\\n"\);',
             'detailPoteaux.append(getString(R.string.ligne_besoin,\n'
             '                        typeBarre,\n'
             '                        getResources().getQuantityString(R.plurals.nb_coupes, nombreParPoteau, nombreParPoteau),\n'
             '                        typeElement.singulier(this),\n'
             '                        getResources().getQuantityString(R.plurals.nb_coupes,\n'
             '                                totalCoupesNecessaires, totalCoupesNecessaires)));',
             "ligne des besoins (coupes/poteau)")
    p.rep_re(r'\+ "=== Optimisé sur l\'ensemble du chantier ==="\);',
             "+ getString(R.string.titre_optimise_chantier));", "titre Optimisé sur l'ensemble du chantier")

    # --- bouton stock
    p.rep('bouton.setText("Annuler utilisation du stock");', "bouton.setText(R.string.bouton_annuler_stock);")
    p.rep('bouton.setText("Confirmer utilisation du stock");', "bouton.setText(R.string.confirmer_utilisation_stock);")

    # --- résumé / détail par type de barre
    p.rep_entre("        StringBuilder resume = new StringBuilder();",
                "        tvResumeBarre.setText(resume.toString());",
                NOUVEAU_RESUME, "bloc résumé (StringBuilder resume)", inclure_fin=True)
    p.rep_re(r'detail\.append\("Barre "\)\.append\(numeroBarre\)\.append\(" : "\)\s*\.append\(formatterDetailBarre\(barre\)\);',
             "detail.append(getString(R.string.barre_numero, numeroBarre, formatterDetailBarre(barre, this)));",
             "ligne Barre N : détail")
    p.rep("tvResumeBarresDetail.setText(formatterResumeBarres(resultat.detailBarres));",
          "tvResumeBarresDetail.setText(formatterResumeBarres(resultat.detailBarres, this));")

    # --- messages du stock (dans des classes anonymes : MainActivity.this.getString)
    p.rep_re(r'"Confirmation impossible : vérifiez le stock\. "\s*\+\s*"L\'opération peut aussi être déjà enregistrée "\s*\+\s*"ou la sauvegarde avoir échoué\."',
             "MainActivity.this.getString(R.string.stock_confirmation_impossible)", "message Confirmation impossible")
    p.rep('"Utilisation du stock confirmée pour " + typeBarre + "."',
          "MainActivity.this.getString(R.string.stock_utilisation_confirmee, typeBarre)")
    p.rep_re(r'"Annulation impossible : le stock a pu être modifié "\s*\+\s*"depuis la confirmation, ou la sauvegarde "\s*\+\s*"a échoué\."',
             "MainActivity.this.getString(R.string.stock_annulation_impossible)", "message Annulation impossible")
    p.rep('"Utilisation du stock annulée pour " + typeBarre + "."',
          "MainActivity.this.getString(R.string.stock_utilisation_annulee, typeBarre)")

    # --- boutons afficher / masquer
    p.rep('btnAfficherDetail.setText(estVisible ? "Afficher détail" : "Masquer détail");',
          "btnAfficherDetail.setText(estVisible ? R.string.btn_afficher_detail : R.string.btn_masquer_detail);")
    p.rep('btnResumer.setText("Résumer");', "btnResumer.setText(R.string.resumer);", tous=True)
    p.rep('btnResumer.setText(estVisible ? "Résumer" : "Masquer résumé");',
          "btnResumer.setText(estVisible ? R.string.resumer : R.string.btn_masquer_resume);")
    p.rep('btnDechetsAuStock.setText("Retirer chutes envoyées");',
          "btnDechetsAuStock.setText(R.string.btn_retirer_chutes);", tous=True)
    p.rep('btnDechetsAuStock.setText("Déchets au stock");', "btnDechetsAuStock.setText(R.string.dechets_au_stock);")
    p.rep('"Déchets de " + typeBarre + " ajoutés au stock."',
          "MainActivity.this.getString(R.string.dechets_ajoutes, typeBarre)")
    p.rep('"Déchets de " + typeBarre + " retirés du stock."',
          "MainActivity.this.getString(R.string.dechets_retires, typeBarre)")

    # --- exports
    p.rep('Toast.makeText(this, "Veuillez d\'abord calculer les résultats.", Toast.LENGTH_SHORT)',
          "Toast.makeText(this, R.string.calculer_d_abord, Toast.LENGTH_SHORT)", tous=True)
    p.rep('"FerPro_" + typeElement.getPlurielMajuscule() + partieClient',
          '"FerPro_" + typeElement.plurielDefiniMajuscule(contexteFrancais()) + partieClient')
    p.rep('startActivity(Intent.createChooser(intent, "Ouvrir le PDF avec"));',
          "startActivity(Intent.createChooser(intent, getString(R.string.ouvrir_pdf_avec)));")
    p.rep('"Aucune application pour ouvrir le PDF. Fichier enregistré : " + fichierPdf.getName(),',
          "getString(R.string.aucune_app_pdf, fichierPdf.getName()),")
    p.rep('Toast.makeText(this, "Erreur lors de la génération du PDF : " + e.getMessage(),',
          "Toast.makeText(this, getString(R.string.erreur_pdf, e.getMessage()),")
    # données du rapport PDF (toujours en français pour l'instant)
    p.rep("        donnees.nomClient = nomClient;\n        donnees.typeSingulier = typeElement.getSingulier();\n"
          "        donnees.typePluriel = typeElement.getPluriel();\n",
          "        donnees.nomClient = nomClient;\n        final Context fr = contexteFrancais();   // le PDF reste en français pour l'instant\n"
          "        donnees.typeSingulier = typeElement.singulier(fr);\n"
          "        donnees.typePluriel = typeElement.plurielDefini(fr);\n",
          libelle="données du rapport PDF (typeSingulier / typePluriel)")
    p.rep("detail.affectation = formatterDescriptionCoupes(barre) + marqueurStock(barre);",
          "detail.affectation = formatterDescriptionCoupes(barre, fr) + marqueurStock(barre, fr);")
    # Excel : en français aussi
    p.rep("        GenerateurXlsx classeur = new GenerateurXlsx();\n",
          "        GenerateurXlsx classeur = new GenerateurXlsx();\n        final Context fr = contexteFrancais();   // l'Excel reste en français pour l'instant\n")
    debut_xl = p.t.find("private void exporterResultatsEnExcel()")
    if debut_xl < 0:
        p.erreurs.append(("MainActivity.java", "méthode exporterResultatsEnExcel introuvable"))
    else:
        fin_xl = p.t.find("    /**", debut_xl)
        zone = p.t[debut_xl:fin_xl]
        zone = zone.replace("typeElement.getPlurielMajuscule()", "typeElement.plurielDefiniMajuscule(fr)")
        zone = zone.replace("typeElement.getSingulier()", "typeElement.singulier(fr)")
        zone = zone.replace("typeElement.getPluriel()", "typeElement.plurielDefini(fr)")
        zone = zone.replace("formatterDescriptionCoupes(barre) + marqueurStock(barre)",
                            "formatterDescriptionCoupes(barre, fr) + marqueurStock(barre, fr)")
        zone = zone.replace('Intent.createChooser(intent, "Ouvrir le fichier Excel avec")',
                            "Intent.createChooser(intent, getString(R.string.ouvrir_excel_avec))")
        zone = re.sub(r'"Aucune application pour ouvrir le fichier Excel\. Fichier enregistré : "\s*\+\s*fichierXlsx\.getName\(\)',
                      lambda _m: "getString(R.string.aucune_app_excel, fichierXlsx.getName())", zone)
        zone = zone.replace('"Erreur lors de la génération du fichier Excel : " + e.getMessage()',
                            "getString(R.string.erreur_excel, e.getMessage())")
        p.t = p.t[:debut_xl] + zone + p.t[fin_xl:]

    # --- formatteurs (nouvelle version avec Context)
    p.rep_entre("    /**\n     * Regroupe et met en forme les coupes d'une barre donnée",
                "    private ResultatCalcul calculerBarresNecessaires(",
                NOUVEAUX_FORMATTEURS, "formatteurs (formatterDescriptionCoupes ... formatterChute)")

    # --- enregistrement pour un client
    p.rep('getSupportActionBar().setTitle("Calcul " + typeElement.getSingulier() + " - " + client.getNom());',
          "getSupportActionBar().setTitle(typeElement.titreCalcul(this, client.getNom()));")
    p.rep_re(r'btnEnregistrerClient\.setText\(calculId != null\s*\?\s*"Enregistrer les modifications"\s*:\s*"Enregistrer pour " \+ client\.getNom\(\)\);',
             "btnEnregistrerClient.setText(calculId != null\n"
             "                ? getString(R.string.btn_enregistrer_modifs)\n"
             "                : getString(R.string.btn_enregistrer_pour, client.getNom()));",
             "bouton Enregistrer pour <client>")
    p.rep('Toast.makeText(this, "Calculez d\'abord avant d\'enregistrer.",',
          "Toast.makeText(this, R.string.calculer_avant_enregistrer,")
    p.rep('etNom.setText("Calcul du " + dateDuJour);', "etNom.setText(getString(R.string.nom_calcul_defaut, dateDuJour));")
    p.rep('.setTitle(calculId != null ? "Enregistrer les modifications" : "Enregistrer le calcul")',
          ".setTitle(calculId != null ? R.string.btn_enregistrer_modifs : R.string.dialog_titre_enregistrer)")
    p.rep('.setNegativeButton("Ignorer", null)', ".setNegativeButton(R.string.dialog_ignorer, null)")
    p.rep('.setPositiveButton("Enregistrer", null)', ".setPositiveButton(R.string.dialog_enregistrer, null)")
    p.rep('layoutNom.setError("Le nom du calcul est obligatoire");',
          "layoutNom.setError(getString(R.string.nom_calcul_obligatoire));")
    p.rep('"L\'enregistrement a échoué."', "MainActivity.this.getString(R.string.enregistrement_echoue)")
    p.rep('btnEnregistrerClient.setText("Enregistrer les modifications");',
          "btnEnregistrerClient.setText(R.string.btn_enregistrer_modifs);")
    p.rep('etaitModification ? "Modifications enregistrées." : "Calcul enregistré."',
          "MainActivity.this.getString(etaitModification ? R.string.modifs_enregistrees : R.string.calcul_enregistre)")
    p.rep('"Impossible d\'enregistrer ce calcul."', "MainActivity.this.getString(R.string.impossible_enregistrer)")

    # --- ouverture d'un calcul enregistré
    p.rep('Toast.makeText(this, "Calcul introuvable.", Toast.LENGTH_SHORT)',
          "Toast.makeText(this, R.string.calcul_introuvable, Toast.LENGTH_SHORT)")
    p.rep_re(r'Toast\.makeText\(this, "Ce calcul enregistré est illisible\.",',
             "Toast.makeText(this, R.string.calcul_illisible,", "message Ce calcul enregistré est illisible")
    p.rep('tvResultats.setText(racine.optString("texteResultats", ""));',
          "tvResultats.setText(construireTexteBesoins());")

    # --- validation des champs
    p.rep_re(r'tvResultats\.setText\(typeElement\.getSingulierMajuscule\(\) \+ " #" \+ numeroGroupe\s*'
             r'\+ ": veuillez remplir le nombre de " \+ typeElement\.getPluriel\(\) \+ " et la longueur"\);',
             "tvResultats.setText(getString(R.string.erreur_remplir_champs,\n"
             "                    typeElement.singulierMajuscule(this), numeroGroupe, typeElement.plurielDefini(this)));",
             "validation : veuillez remplir le nombre de ...")
    p.rep_re(r'tvResultats\.setText\(typeElement\.getSingulierMajuscule\(\) \+ " #" \+ numeroGroupe\s*'
             r'\+ ": les valeurs doivent être positives"\);',
             "tvResultats.setText(getString(R.string.erreur_valeurs_positives,\n"
             "                        typeElement.singulierMajuscule(this), numeroGroupe));",
             "validation : valeurs positives")
    p.rep_re(r'tvResultats\.setText\(typeElement\.getSingulierMajuscule\(\) \+ " #" \+ numeroGroupe\s*'
             r'\+ ": veuillez entrer des nombres valides"\);',
             "tvResultats.setText(getString(R.string.erreur_nombres_valides,\n"
             "                    typeElement.singulierMajuscule(this), numeroGroupe));",
             "validation : nombres valides")

    # --- méthodes utilitaires
    p.rep("    private int getValeur(TextInputEditText editText) {",
          NOUVEAUX_UTILITAIRES + "    private int getValeur(TextInputEditText editText) {")

    # --- contrôle final : plus d'anciens accesseurs ni d'anciens appels
    for motif in (r"typeElement\.get(Singulier|Pluriel|PlurielMajuscule|SingulierMajuscule|LeSingulier|UnAutre|Ce)\(\)",
                  r"formatterDescriptionCoupes\(barre\)", r"formatterDetailBarre\(barre\)",
                  r"formatterResumeBarres\([a-zA-Z.]+\)\)", r"marqueurStock\(barre\)"):
        for m in re.finditer(motif, p.t):
            p.erreurs.append(("MainActivity.java", "reste non converti : " + m.group(0)))
    return p


def patcher_calculs_client(src):
    p = Patcheur(src, "CalculsClientActivity.java")
    p.rep_re(r'tvDate\.setText\(calcul\.getTypeElement\(\)\.getSingulierMajuscule\(\) \+ " · "\s*'
             r'\+ \(modifie \? "Modifié le " : "Enregistré le "\)\s*'
             r'\+ format\.format\(new Date\(calcul\.getDateModification\(\)\)\)\);',
             "tvDate.setText(calcul.getTypeElement().singulierMajuscule(CalculsClientActivity.this) + \" · \"\n"
             "                    + getString(modifie ? R.string.calcul_modifie_le : R.string.calcul_enregistre_le,\n"
             "                    format.format(new Date(calcul.getDateModification()))));",
             "libellé du calcul dans la liste (type · Modifié le ...)")
    return p


def ajouter_text_direction(src, nom, ids):
    erreurs = []
    for identifiant in ids:
        marque = 'android:id="@+id/%s"' % identifiant
        i = src.find(marque)
        if i < 0:
            erreurs.append((nom, "id introuvable : " + identifiant))
            continue
        fin_balise = src.find(">", i)
        if "android:textDirection" in src[i:fin_balise]:
            continue
        # indentation de la ligne de l'id
        debut_ligne = src.rfind("\n", 0, i) + 1
        indent = re.match(r"\s*", src[debut_ligne:i]).group(0)
        src = src[:i + len(marque)] + '\n' + indent + 'android:textDirection="locale"' + src[i + len(marque):]
    return src, erreurs


# =====================================================================================
#  Programme principal
# =====================================================================================

def main():
    racine = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    principal = racine / "app" / "src" / "main"
    res = principal / "res"
    java_dir = principal / "java" / "com" / "example" / "gkm_bar_fer"
    if not java_dir.is_dir() or not res.is_dir():
        print("ERREUR : projet introuvable (lance le script depuis le dossier qui contient « app »).")
        sys.exit(1)

    fichiers = {
        "main": java_dir / "MainActivity.java",
        "client": java_dir / "CalculsClientActivity.java",
        "type": java_dir / "TypeElement.java",
        "layout_main": res / "layout" / "activity_main.xml",
        "layout_res": res / "layout" / "item_resultat_barre.xml",
    }
    for nom, chemin in fichiers.items():
        if not chemin.exists():
            print("ERREUR : fichier introuvable : %s" % chemin)
            sys.exit(1)

    texte_main = fichiers["main"].read_text(encoding="utf-8")
    if "R.string.entete_groupe" in texte_main:
        print("Cette étape est déjà appliquée (MainActivity utilise déjà les nouvelles ressources). Rien à faire.")
        sys.exit(0)

    # L'étape 2 doit avoir été lancée avant (elle crée des textes réutilisés ici).
    toutes_les_ressources = "".join(x.read_text(encoding="utf-8") for x in res.glob("values/*.xml"))
    manquantes = [k for k in ("resumer", "dechets_au_stock", "confirmer_utilisation_stock")
                  if 'name="%s"' % k not in toutes_les_ressources]
    if manquantes:
        print("ERREUR : l'étape 2 n'a pas été appliquée (ressources manquantes : %s)." % ", ".join(manquantes))
        print("Lance d'abord :  python i18n_etape2.py   puis relance ce script.")
        sys.exit(1)

    # Les noms de ressources ne doivent pas déjà exister ailleurs.
    nos_fichiers = {"types.xml", "calcul.xml"}
    deja = set()
    for xml in list(res.glob("values*/*.xml")):
        if xml.name in nos_fichiers:
            continue
        deja |= set(re.findall(r'<(?:string|plurals)\s+name="([^"]+)"', xml.read_text(encoding="utf-8")))
    conflits = sorted(noms_definis() & deja)
    if conflits:
        print("ERREUR : ces ressources existent déjà (renomme-les ou supprime-les) : %s" % ", ".join(conflits))
        sys.exit(1)

    # --- préparation en mémoire (rien n'est écrit tant que tout n'est pas bon)
    p_main = patcher_main(texte_main)
    p_client = patcher_calculs_client(fichiers["client"].read_text(encoding="utf-8"))
    lm, e1 = ajouter_text_direction(fichiers["layout_main"].read_text(encoding="utf-8"),
                                    "activity_main.xml", ["tvResultats"])
    lr, e2 = ajouter_text_direction(fichiers["layout_res"].read_text(encoding="utf-8"),
                                    "item_resultat_barre.xml",
                                    ["tvResumeBarre", "tvDetailBarres", "tvResumeBarresDetail"])
    erreurs = p_main.erreurs + p_client.erreurs + e1 + e2

    if erreurs:
        print("ARRÊT : %d endroit(s) du code ne correspondent pas à ce que le script attend." % len(erreurs))
        print("Rien n'a été modifié. Détail :")
        for nom, detail in erreurs:
            print("  - %s : %s" % (nom, detail))
        print("\nEnvoie-moi ce message (et ton fichier MainActivity.java si besoin) : je l'adapte.")
        sys.exit(2)

    # --- tout est généré AVANT d'écrire quoi que ce soit
    contenu_type = generer_type_element()
    contenus_res = {
        res / "values" / "types.xml": generer_types("fr"),
        res / "values-ar" / "types.xml": generer_types("ar"),
        res / "values" / "calcul.xml": generer_calcul("fr"),
        res / "values-ar" / "calcul.xml": generer_calcul("ar"),
    }

    # --- sauvegarde puis écriture
    sauvegarde = racine / ("i18n3_sauvegarde_" + datetime.now().strftime("%Y%m%d_%H%M%S"))
    sauvegarde.mkdir(parents=True, exist_ok=True)
    for nom, chemin in fichiers.items():
        shutil.copy2(chemin, sauvegarde / chemin.name)
    print("Sauvegarde : %s" % sauvegarde)

    fichiers["main"].write_text(p_main.t, encoding="utf-8", newline="\n")
    fichiers["client"].write_text(p_client.t, encoding="utf-8", newline="\n")
    fichiers["type"].write_text(contenu_type, encoding="utf-8", newline="\n")
    fichiers["layout_main"].write_text(lm, encoding="utf-8", newline="\n")
    fichiers["layout_res"].write_text(lr, encoding="utf-8", newline="\n")

    (res / "values-ar").mkdir(exist_ok=True)
    for chemin, contenu in contenus_res.items():
        chemin.write_text(contenu, encoding="utf-8", newline="\n")

    print("\nFichiers créés   : res/values/types.xml, res/values-ar/types.xml,")
    print("                   res/values/calcul.xml, res/values-ar/calcul.xml")
    print("Fichiers modifiés: TypeElement.java, MainActivity.java, CalculsClientActivity.java,")
    print("                   activity_main.xml, item_resultat_barre.xml")
    print("\nTerminé. Dans Android Studio : File > Sync Project with Gradle Files, puis Build > Rebuild Project.")


if __name__ == "__main__":
    main()