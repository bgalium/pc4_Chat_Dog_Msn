package uni.cc4p1.client;

import uni.cc4p1.client.gui.LoginFrame;
import javax.swing.SwingUtilities;

public class DesktopApp {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido. Usando por defecto: " + DEFAULT_PORT);
            }
        }

        final String finalHost = host;
        final int finalPort = port;

        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(
                    javax.swing.UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception ignored) {}

            LoginFrame loginFrame = new LoginFrame(finalHost, finalPort);
            loginFrame.setVisible(true);
        });
    }
}
