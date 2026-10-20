#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Étape 2 de la traduction (français -> arabe) : textes des ÉCRANS (fichiers XML).

Ce que fait le script, à lancer UNE FOIS depuis le dossier du projet (celui qui contient "app") :
  1. sauvegarde res/layout, res/menu et res/values/strings.xml dans "i18n_sauvegarde_<date>"
  2. remplace chaque texte écrit en dur dans res/layout et res/menu
     (android:text, android:hint, android:title, android:contentDescription...)
     par une référence  @string/nom
  3. ajoute les textes français dans res/values/strings.xml
  4. crée / complète res/values-ar/strings.xml avec les traductions arabes
  5. affiche un rapport (textes remplacés, textes inconnus laissés tels quels)

Le script peut être relancé sans danger : il ne remplace que ce qui est encore écrit en dur.

Utilisation (Terminal Android Studio, à la racine du projet) :
    python i18n_etape2.py
    (ou : py i18n_etape2.py)
"""

import html
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

# --------------------------------------------------------------------------------------
# Dictionnaire : texte français -> (nom de la ressource, traduction arabe)
# --------------------------------------------------------------------------------------
TEXTES = {
    # ---- Page client : boutons de calcul
    "Calculer Poteau": ("calculer_poteau", "حساب الأعمدة"),
    "Calculer Dalle": ("calculer_dalle", "حساب البلاطات"),
    "Calculer Semelle": ("calculer_semelle", "حساب الأسس"),
    "Calculer Escalier": ("calculer_escalier", "حساب الدرج"),
    "Calculer Poutre": ("calculer_poutre", "حساب الروافد"),
    "Calculer Pré-poteau": ("calculer_pre_poteau", "حساب الأعمدة المسبقة الصنع"),
    "Aucun calcul enregistré pour ce client.": ("aucun_calcul_client", "لا توجد حسابات محفوظة لهذا الحريف."),

    # ---- Espace client
    "+ Ajouter Client": ("ajouter_client", "+ إضافة حريف"),
    "Rechercher un client par nom": ("rechercher_client", "البحث عن حريف بالاسم"),
    "Aucun client pour l'instant.": ("aucun_client", "لا يوجد حرفاء حاليا."),
    "Supprimer le client": ("supprimer_client", "حذف الحريف"),
    "Consulter": ("consulter", "عرض"),
    "Ouvrir": ("ouvrir", "فتح"),
    "Nom du client *": ("nom_client_requis", "اسم الحريف *"),
    "Ville (optionnel)": ("ville_optionnel", "المدينة (اختياري)"),
    "Nom du calcul *": ("nom_calcul_requis", "اسم الحساب *"),

    # ---- Écran de calcul
    "Utiliser le stock": ("utiliser_stock", "استعمال المخزون"),
    "Utiliser les chutes disponibles avant d’acheter": ("utiliser_chutes_desc", "استعمال الفضلات المتوفرة قبل الشراء"),
    "Synthèse du calcul et optimisation": ("synthese_calcul", "ملخص الحساب والتحسين"),
    "Enregistrer pour ce client": ("enregistrer_pour_client", "حفظ لهذا الحريف"),
    "Exporter PDF": ("exporter_pdf", "تصدير PDF"),
    "Exporter Excel": ("exporter_excel", "تصدير Excel"),
    "Définissez les dimensions et les armatures": ("definir_dimensions", "حدّد الأبعاد والتسليح"),
    "Ajoutez les diamètres et indiquez la quantité nécessaire": ("ajouter_diametres", "أضف الأقطار وحدّد الكمية اللازمة"),
    "Quantité": ("quantite", "الكمية"),
    "Supprimer": ("supprimer", "حذف"),
    "m": ("unite_metre", "م"),
    "Afficher le détail": ("afficher_detail", "عرض التفاصيل"),
    "Déchets au stock": ("dechets_au_stock", "الفضلات إلى المخزون"),
    "Confirmer utilisation du stock": ("confirmer_utilisation_stock", "تأكيد استعمال المخزون"),
    "Résumer": ("resumer", "تلخيص"),

    # ---- Accueil
    "VOTRE ESPACE CHANTIER": ("espace_chantier", "فضاء ورشتك"),
    "Chaque barre compte.": ("slogan_barre", "كل قضيب له قيمته."),
    "Préparez vos coupes et gardez une vue claire sur votre stock de fer.": (
        "menu_intro", "حضّر قطعاتك وحافظ على رؤية واضحة لمخزون الحديد."),
    "Calcul des poteaux": ("calcul_poteaux", "حساب الأعمدة"),
    "Renseignez vos dimensions et vos armatures pour obtenir le détail des barres nécessaires.": (
        "calcul_poteaux_desc", "أدخل الأبعاد والتسليح للحصول على تفاصيل القضبان اللازمة."),
    "Démarrer un calcul": ("demarrer_calcul", "بدء حساب"),
    "Autres calculs": ("autres_calculs", "حسابات أخرى"),
    "Escalier": ("type_escalier", "درج"),
    "À venir": ("a_venir", "قريبا"),
    "Dalle": ("type_dalle", "بلاطة"),
    "Disponible": ("disponible", "متاح"),
    "Semelle": ("type_semelle", "أساس"),
    "Poutre": ("type_poutre", "رافدة"),
    "Pré-poteau": ("type_pre_poteau", "عمود مسبق الصنع"),
    "Votre stock": ("votre_stock", "مخزونك"),
    "Valorisez vos chutes.": ("valorisez_chutes", "ثمّن فضلاتك."),
    "Consultez et ajoutez vos barres entières et vos morceaux de fer.": (
        "stock_desc", "اطّلع على قضبانك الكاملة وقطع الحديد وأضفها."),
    "Consulter le stock": ("consulter_stock", "عرض المخزون"),

    # ---- Stock déchets
    "Ajouter Fer": ("ajouter_fer", "إضافة حديد"),
    "Historique": ("historique", "السجل"),
    "Retirer Fer": ("retirer_fer", "سحب حديد"),
    "Filtrer par type de fer": ("filtrer_type_fer", "التصفية حسب نوع الحديد"),
    "Aucun fer en stock pour l'instant.": ("aucun_fer_stock", "لا يوجد حديد في المخزون حاليا."),
    "Ajouter du fer au stock": ("ajouter_fer_stock", "إضافة حديد إلى المخزون"),
    "Sélectionnez le type de fer et l’unité à ajouter.": (
        "selectionner_type_unite", "اختر نوع الحديد والوحدة المراد إضافتها."),
    "Type de fer": ("type_fer", "نوع الحديد"),
    "Sélectionner un type": ("selectionner_type", "اختر نوعا"),
    "Unité": ("unite", "الوحدة"),
    "Sélectionner une unité": ("selectionner_unite", "اختر وحدة"),
    "Aucun mouvement enregistré pour l'instant.": ("aucun_mouvement", "لا توجد حركات مسجلة حاليا."),
    "Historique du stock": ("historique_stock", "سجل المخزون"),
    "Longueur du morceau": ("longueur_morceau", "طول القطعة"),
    "Longueur requise": ("longueur_requise", "الطول المطلوب"),
    "Ex : 5,30": ("exemple_longueur", "مثال : 5,30"),
    "Nombre de morceaux à retirer": ("nombre_morceaux_retirer", "عدد القطع المراد سحبها"),
    "Longueur des coupes (m)": ("longueur_coupes_m", "طول القطع (م)"),
}

# Textes à laisser tels quels (noms de produits, repères techniques...)
A_IGNORER = {"N6", "N8", "N10", "N12", "N14", "N16", "Fer Pro"}

# Ressources qui existent déjà en français : on ajoute seulement leur traduction arabe.
TRADUCTIONS_EXISTANTES = {
    "infos_poteaux": "معلومات الأعمدة",
    "nombre_poteaux": "عدد الأعمدة",
    "longueur_poteaux": "طول العمود",
    "barres_fer_poteau": "قضبان الحديد لكل عمود",
    "nombre": "العدد",
    "calculer": "حساب",
    "selection_types_barres": "اختيار أنواع القضبان",
    "choisir_type_barre": "اختر نوع القضيب",
    "aucune_barre_selectionnee": "لم يتم اختيار أي قضيب !",
    "resultats": "النتائج",
    "ajouter_poteau": "إضافة عمود آخر",
    "supprimer_poteau": "حذف هذا العمود",
}

# Ressources qui ne se traduisent pas (marquées translatable="false").
NON_TRADUISIBLES = {"app_name", "n6", "n8", "n10", "n12", "n14", "n16"}

ATTRIBUTS = r"(?:android|app):(?:text|hint|title|contentDescription|placeholderText|helperText|prefixText|suffixText|label)"
MOTIF_ATTRIBUT = re.compile(r'(\b%s=")([^"]*)(")' % ATTRIBUTS)
A_DES_LETTRES = re.compile(r"[A-Za-zÀ-ÿ\u0600-\u06FF]")


def echapper(texte):
    """Échappe un texte pour strings.xml (apostrophes, &, <, guillemets, @ ou ? au début)."""
    t = texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    t = t.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if t.startswith("@") or t.startswith("?"):
        t = "\\" + t
    return t


def lire_strings(chemin):
    """Retourne {nom: texte brut tel qu'écrit dans le fichier} (ordre conservé)."""
    resultat = {}
    if chemin.exists():
        contenu = chemin.read_text(encoding="utf-8")
        for m in re.finditer(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', contenu, re.S):
            resultat[m.group(1)] = m.group(2)
    return resultat


def main():
    racine = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    res = racine / "app" / "src" / "main" / "res"
    if not res.is_dir():
        print("ERREUR : dossier introuvable : %s" % res)
        print("Lance le script depuis le dossier du projet (celui qui contient « app »).")
        sys.exit(1)

    dossiers_xml = [res / "layout", res / "menu"]
    fichier_fr = res / "values" / "strings.xml"
    fichier_ar = res / "values-ar" / "strings.xml"

    # --- 1) sauvegarde
    sauvegarde = racine / ("i18n_sauvegarde_" + datetime.now().strftime("%Y%m%d_%H%M%S"))
    for d in dossiers_xml:
        if d.is_dir():
            shutil.copytree(d, sauvegarde / d.name, dirs_exist_ok=True)
    (sauvegarde / "values").mkdir(parents=True, exist_ok=True)
    if fichier_fr.exists():
        shutil.copy2(fichier_fr, sauvegarde / "values" / "strings.xml")
    if fichier_ar.exists():
        (sauvegarde / "values-ar").mkdir(parents=True, exist_ok=True)
        shutil.copy2(fichier_ar, sauvegarde / "values-ar" / "strings.xml")
    print("Sauvegarde : %s" % sauvegarde)

    # --- 2) remplacement des textes en dur
    utilises = {}      # nom -> (français, arabe)
    inconnus = {}      # texte -> fichiers
    remplaces = 0

    for dossier in dossiers_xml:
        if not dossier.is_dir():
            continue
        for fichier in sorted(dossier.glob("*.xml")):
            brut = fichier.read_text(encoding="utf-8")

            def remplacer(m):
                nonlocal remplaces
                valeur = html.unescape(m.group(2))
                if valeur.startswith("@") or valeur.startswith("?") or not A_DES_LETTRES.search(valeur):
                    return m.group(0)
                if valeur in A_IGNORER:
                    return m.group(0)
                if valeur in TEXTES:
                    nom, arabe = TEXTES[valeur]
                    utilises[nom] = (valeur, arabe)
                    remplaces += 1
                    return m.group(1) + "@string/" + nom + m.group(3)
                inconnus.setdefault(valeur, set()).add(fichier.name)
                return m.group(0)

            nouveau = MOTIF_ATTRIBUT.sub(remplacer, brut)
            if nouveau != brut:
                fichier.write_text(nouveau, encoding="utf-8", newline="\n")

    # --- 3) strings.xml (français)
    contenu_fr = fichier_fr.read_text(encoding="utf-8") if fichier_fr.exists() else (
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n')
    existants_fr = lire_strings(fichier_fr)

    for nom in NON_TRADUISIBLES:
        contenu_fr = re.sub(r'<string name="%s"(?![^>]*translatable)' % nom,
                            '<string name="%s" translatable="false"' % nom, contenu_fr)

    lignes_fr = []
    for nom, (francais, _arabe) in utilises.items():
        if nom not in existants_fr:
            lignes_fr.append('    <string name="%s">%s</string>' % (nom, echapper(francais)))
    if lignes_fr:
        bloc = "\n    <!-- Textes des écrans (étape 2) -->\n" + "\n".join(lignes_fr) + "\n"
        contenu_fr = contenu_fr.replace("</resources>", bloc + "</resources>")
    fichier_fr.write_text(contenu_fr, encoding="utf-8", newline="\n")

    # --- 4) values-ar/strings.xml (arabe)
    fichier_ar.parent.mkdir(parents=True, exist_ok=True)
    existants_ar = lire_strings(fichier_ar)
    contenu_ar = fichier_ar.read_text(encoding="utf-8") if fichier_ar.exists() else (
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n')

    lignes_ar = []
    for nom, (_francais, arabe) in utilises.items():
        if nom not in existants_ar:
            lignes_ar.append('    <string name="%s">%s</string>' % (nom, echapper(arabe)))
    for nom, arabe in TRADUCTIONS_EXISTANTES.items():
        if nom in lire_strings(fichier_fr) and nom not in existants_ar:
            lignes_ar.append('    <string name="%s">%s</string>' % (nom, echapper(arabe)))
    if lignes_ar:
        bloc = "\n    <!-- Textes des écrans (étape 2) -->\n" + "\n".join(lignes_ar) + "\n"
        contenu_ar = contenu_ar.replace("</resources>", bloc + "</resources>")
    fichier_ar.write_text(contenu_ar, encoding="utf-8", newline="\n")

    # --- 5) rapport
    print("\nTextes remplacés par des @string : %d" % remplaces)
    print("Nouvelles ressources françaises : %d" % len(lignes_fr))
    print("Nouvelles traductions arabes    : %d" % len(lignes_ar))
    if inconnus:
        print("\nATTENTION - textes non reconnus (laissés tels quels dans les layouts) :")
        for texte, fichiers in inconnus.items():
            print("  - %r  (%s)" % (texte, ", ".join(sorted(fichiers))))
        print("Envoie-moi cette liste : je te donne leur traduction.")
    else:
        print("\nAucun texte non reconnu : tous les écrans sont prêts pour l'arabe.")


if __name__ == "__main__":
    main()