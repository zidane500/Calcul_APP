package ferpro.generator;

import com.example.gkm_bar_fer.licence.LicenseDates;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Fenêtre principale de FER PRO LICENSE GENERATOR. */
public final class GeneratorWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private final KeyVault.Cles cles;
    private final Path dossier;

    private final JTextField champClient = new JTextField();
    private final JTextField champId = new JTextField();
    private final JComboBox<LicenseService.Duree> listeDuree =
            new JComboBox<>(LicenseService.Duree.values());
    private final JTextField champDebut = new JTextField();
    private final JTextField champFin = new JTextField();
    private final JTextArea zoneCode = new JTextArea(6, 40);
    private final JLabel etiquetteStatut = new JLabel(" ");

    public GeneratorWindow(KeyVault.Cles cles, Path dossier) {
        super("FER PRO LICENSE GENERATOR");
        this.cles = cles;
        this.dossier = dossier;
        construire();
    }

    private void construire() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel formulaire = new JPanel(new GridBagLayout());
        formulaire.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        JLabel titre = new JLabel("FER PRO LICENSE GENERATOR");
        titre.setFont(titre.getFont().deriveFont(Font.BOLD, 18f));
        GridBagConstraints t = base(0);
        t.gridwidth = 2;
        t.insets = new Insets(0, 0, 14, 0);
        formulaire.add(titre, t);

        ajouterLigne(formulaire, 1, "Client (facultatif)", champClient);
        ajouterLigne(formulaire, 2, "Installation ID", champId);
        ajouterLigne(formulaire, 3, "Durée", listeDuree);
        ajouterLigne(formulaire, 4, "Date début (jj/mm/aaaa)", champDebut);
        ajouterLigne(formulaire, 5, "Date expiration", champFin);

        JLabel aide = new JLabel("<html><i>L'expiration est calculée automatiquement, mais tu peux la modifier "
                + "(ex. renouvellement : ancienne expiration + durée).</i></html>");
        GridBagConstraints a = base(6);
        a.gridwidth = 2;
        formulaire.add(aide, a);

        JButton boutonGenerer = new JButton("GÉNÉRER");
        boutonGenerer.setFont(boutonGenerer.getFont().deriveFont(Font.BOLD, 14f));
        GridBagConstraints g = base(7);
        g.gridwidth = 2;
        g.insets = new Insets(14, 0, 8, 0);
        formulaire.add(boutonGenerer, g);

        JLabel titreCode = new JLabel("Licence générée :");
        GridBagConstraints tc = base(8);
        tc.gridwidth = 2;
        formulaire.add(titreCode, tc);

        zoneCode.setLineWrap(true);
        zoneCode.setWrapStyleWord(false);
        zoneCode.setEditable(false);
        zoneCode.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        GridBagConstraints z = base(9);
        z.gridwidth = 2;
        z.fill = GridBagConstraints.BOTH;
        z.weighty = 1;
        formulaire.add(new JScrollPane(zoneCode), z);

        GridBagConstraints s = base(10);
        s.gridwidth = 2;
        formulaire.add(etiquetteStatut, s);

        JPanel boutons = new JPanel();
        JButton boutonCopier = new JButton("COPIER");
        JButton boutonClePublique = new JButton("Clé publique…");
        boutons.add(boutonCopier);
        boutons.add(boutonClePublique);

        getContentPane().add(formulaire, BorderLayout.CENTER);
        getContentPane().add(boutons, BorderLayout.SOUTH);

        // Valeurs par défaut
        LocalDate aujourdhui = LocalDate.now();
        int jour = LicenseDates.jourEpoch(aujourdhui.getYear(), aujourdhui.getMonthValue(),
                aujourdhui.getDayOfMonth());
        champDebut.setText(LicenseDates.formater(jour));
        listeDuree.setSelectedItem(LicenseService.Duree.UN_AN);
        recalculerFin();

        // Événements
        listeDuree.addActionListener(e -> recalculerFin());
        champDebut.addActionListener(e -> recalculerFin());
        champDebut.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                recalculerFin();
            }
        });
        boutonGenerer.addActionListener(e -> generer());
        boutonCopier.addActionListener(e -> copier());
        boutonClePublique.addActionListener(e -> afficherClePublique());

        getRootPane().setDefaultButton(boutonGenerer);
        setMinimumSize(new Dimension(560, 560));
        pack();
        setLocationRelativeTo(null);
    }

    private static GridBagConstraints base(int ligne) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = ligne;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 0, 4, 0);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static void ajouterLigne(JPanel p, int ligne, String libelle, java.awt.Component champ) {
        GridBagConstraints l = base(ligne);
        l.weightx = 0;
        l.insets = new Insets(4, 0, 4, 12);
        p.add(new JLabel(libelle), l);
        GridBagConstraints c = base(ligne);
        c.gridx = 1;
        p.add(champ, c);
    }

    /** Met à jour la date d'expiration selon la date de début et la durée choisies. */
    private void recalculerFin() {
        LicenseService.Duree duree = (LicenseService.Duree) listeDuree.getSelectedItem();
        if (duree == null) {
            return;
        }
        if (duree.estAVie()) {
            champFin.setText("Sans expiration");
            champFin.setEnabled(false);
            return;
        }
        champFin.setEnabled(true);
        try {
            int debut = LicenseService.lireDate(champDebut.getText());
            champFin.setText(LicenseDates.formater(LicenseService.calculerFin(debut, duree)));
        } catch (IllegalArgumentException e) {
            champFin.setText("");
        }
    }

    private void generer() {
        try {
            LicenseService.Duree duree = (LicenseService.Duree) listeDuree.getSelectedItem();
            int debut = LicenseService.lireDate(champDebut.getText());
            int fin = duree.estAVie() ? debut : LicenseService.lireDate(champFin.getText());

            LocalDate local = LocalDate.now();
            int aujourdhui = LicenseDates.jourEpoch(local.getYear(), local.getMonthValue(),
                    local.getDayOfMonth());

            if (!duree.estAVie() && fin < debut) {
                throw new IllegalArgumentException("La date d'expiration est avant la date de début.");
            }
            if (!duree.estAVie() && fin < aujourdhui && !confirmer(
                    "Cette licence est déjà expirée (" + LicenseDates.formater(fin) + "). Continuer ?")) {
                return;
            }
            if (debut > aujourdhui + 1 && !confirmer(
                    "La date de début est dans le futur. L'application refusera ce code avant cette date. "
                            + "Continuer ?")) {
                return;
            }

            LicenseService.Resultat r = LicenseService.generer(
                    cles.privee, cles.publique, champId.getText(), duree, debut, fin);

            zoneCode.setText(r.code);
            zoneCode.setCaretPosition(0);
            etiquetteStatut.setText("<html>\u2714 Licence <b>" + duree + "</b> pour <b>"
                    + r.licence.getInstallationId() + "</b>, "
                    + LicenseService.resumePeriode(r.licence) + " (auto-vérifiée)</html>");
            champId.setText(r.licence.getInstallationId());

            try {
                LicenseService.ajouterAuJournal(dossier.resolve(KeyVault.NOM_JOURNAL),
                        champClient.getText(), r.licence, aujourdhui);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "La licence est générée, mais le journal n'a pas pu être mis à jour :\n"
                                + e.getMessage()
                                + "\n(Ferme le fichier journal s'il est ouvert dans Excel.)",
                        "Journal", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Données invalides",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erreur",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean confirmer(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Confirmation",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void copier() {
        String code = zoneCode.getText();
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Génère d'abord une licence.", "Copier",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(code), null);
        etiquetteStatut.setText("\u2714 Licence copiée dans le presse-papiers.");
    }

    private void afficherClePublique() {
        GeneratorApp.afficherClePublique(this, cles);
    }
}