package ferpro.generator;

import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.BoxLayout;

/**
 * Point d'entrée de FER PRO LICENSE GENERATOR.
 *
 * Premier lancement : crée ta paire de clés (la clé privée est chiffrée par mot de passe,
 * dans C:\Users\TonNom\FerPro-Licences\). Lancements suivants : demande le mot de passe.
 */
public final class GeneratorApp {

    private static final int MOT_DE_PASSE_MIN = 10;
    private static final int ESSAIS_MAX = 3;

    private GeneratorApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GeneratorApp::demarrer);
    }

    private static void demarrer() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
            // Look par défaut de Java : sans importance.
        }
        Path dossier = KeyVault.dossierParDefaut();
        Path fichierCle = dossier.resolve(KeyVault.NOM_FICHIER_PRIVE);

        KeyVault.Cles cles = Files.exists(fichierCle)
                ? deverrouiller(fichierCle)
                : creerNouvellesCles(dossier, fichierCle);
        if (cles == null) {
            System.exit(0);
            return;
        }
        new GeneratorWindow(cles, dossier).setVisible(true);
    }

    private static KeyVault.Cles deverrouiller(Path fichierCle) {
        for (int essai = 1; essai <= ESSAIS_MAX; essai++) {
            char[] mdp = demanderMotDePasse("Mot de passe de ta clé privée Fer Pro :");
            if (mdp == null) {
                return null;
            }
            try {
                return KeyVault.charger(fichierCle, mdp);
            } catch (GeneralSecurityException | IOException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Clé privée",
                        JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(mdp, ' ');
            }
        }
        return null;
    }

    private static KeyVault.Cles creerNouvellesCles(Path dossier, Path fichierCle) {
        JOptionPane.showMessageDialog(null,
                "<html><b>Premier lancement : création de ta clé privée Fer Pro.</b><br><br>"
                        + "Elle sera enregistrée, chiffrée par un mot de passe, ici :<br>" + dossier + "<br><br>"
                        + "&bull; Choisis un mot de passe long (" + MOT_DE_PASSE_MIN + " caractères minimum).<br>"
                        + "&bull; Si tu l'oublies, la clé est perdue : AUCUNE récupération possible.<br>"
                        + "&bull; Fais ensuite 2 copies du fichier .key (clé USB) dans des endroits sûrs.</html>",
                "Création de la clé", JOptionPane.INFORMATION_MESSAGE);

        char[] mdp = null;
        while (true) {
            mdp = demanderMotDePasse("Choisis ton mot de passe :");
            if (mdp == null) {
                return null;
            }
            if (mdp.length < MOT_DE_PASSE_MIN) {
                JOptionPane.showMessageDialog(null,
                        "Mot de passe trop court (" + MOT_DE_PASSE_MIN + " caractères minimum).",
                        "Mot de passe", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            char[] confirmation = demanderMotDePasse("Confirme ton mot de passe :");
            if (confirmation == null) {
                return null;
            }
            boolean identiques = Arrays.equals(mdp, confirmation);
            Arrays.fill(confirmation, ' ');
            if (identiques) {
                break;
            }
            JOptionPane.showMessageDialog(null, "Les deux mots de passe sont différents.",
                    "Mot de passe", JOptionPane.WARNING_MESSAGE);
        }

        try {
            KeyVault.Cles cles = KeyVault.generer();
            KeyVault.sauvegarder(fichierCle, cles, mdp);
            Files.write(dossier.resolve(KeyVault.NOM_FICHIER_PUBLIC),
                    KeyVault.clePubliqueBase32(cles.publique).getBytes(StandardCharsets.UTF_8));
            afficherClePublique(null, cles);
            return cles;
        } catch (GeneralSecurityException | IOException e) {
            JOptionPane.showMessageDialog(null, "Impossible de créer la clé : " + e.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return null;
        } finally {
            Arrays.fill(mdp, ' ');
        }
    }

    /** Affiche la clé publique à coller dans LicenseConstants.PUBLIC_KEY_BASE32 (Android). */
    static void afficherClePublique(Component parent, KeyVault.Cles cles) {
        String cle = KeyVault.clePubliqueBase32(cles.publique);
        JTextArea zone = new JTextArea(cle, 6, 48);
        zone.setLineWrap(true);
        zone.setEditable(false);
        zone.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JButton copier = new JButton("Copier la clé publique");
        copier.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(cle), null));

        JPanel panneau = new JPanel();
        panneau.setLayout(new BoxLayout(panneau, BoxLayout.Y_AXIS));
        panneau.add(new JLabel("<html>Clé PUBLIQUE (sans danger) : à coller dans <b>LicenseConstants.java</b>,<br>"
                + "constante <b>PUBLIC_KEY_BASE32</b>, entre les guillemets.</html>"));
        panneau.add(new JScrollPane(zone));
        panneau.add(copier);
        JOptionPane.showMessageDialog(parent, panneau, "Clé publique", JOptionPane.INFORMATION_MESSAGE);
    }

    private static char[] demanderMotDePasse(String message) {
        JPasswordField champ = new JPasswordField(24);
        int choix = JOptionPane.showConfirmDialog(null, new Object[]{message, champ},
                "FER PRO LICENSE GENERATOR", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        return choix == JOptionPane.OK_OPTION ? champ.getPassword() : null;
    }
}