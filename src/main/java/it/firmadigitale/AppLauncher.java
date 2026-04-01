package it.firmadigitale;

import java.security.Security;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class AppLauncher {

    private AppLauncher() {
    }

    public static void main(String[] args) {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        if (args.length > 0) {
            try {
                new CliRunner(new SignatureService()).run(args);
            } catch (Exception ex) {
                System.err.println("Errore: " + ex.getMessage());
                System.exit(1);
            }
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> new SignatureFrame(new SignatureService()).setVisible(true));
    }
}
