package uni.cc4p1.client.gui;

import uni.cc4p1.client.connection.Connection;
import uni.cc4p1.client.connection.CryptoManager;
import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LoginFrame extends JFrame {
    private final JTextField txtUsername;
    private final JButton btnConnect;
    private final JLabel lblStatus;
    private final String host;
    private final int port;

    public LoginFrame(String host, int port) {
        this.host = host;
        this.port = port;

        setTitle("Dog Messenger - Iniciar Sesión");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 300);
        setLocationRelativeTo(null);
        setResizable(false);

        Color bgColor = new Color(28, 30, 38);
        Color cardColor = new Color(37, 40, 52);
        Color fgColor = new Color(220, 224, 232);
        Color accentColor = new Color(92, 124, 250);
        Color btnHoverColor = new Color(112, 142, 255);
        Color inputColor = new Color(48, 52, 68);

        Font titleFont = new Font("Segoe UI", Font.BOLD, 22);
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font inputFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font buttonFont = new Font("Segoe UI", Font.BOLD, 14);

        JPanel contentPane = new JPanel();
        contentPane.setBackground(bgColor);
        contentPane.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPane.setLayout(new BorderLayout(0, 15));
        setContentPane(contentPane);

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(bgColor);
        JLabel lblTitle = new JLabel("Dog Messenger 🐾");
        lblTitle.setFont(titleFont);
        lblTitle.setForeground(accentColor);
        headerPanel.add(lblTitle);
        contentPane.add(headerPanel, BorderLayout.NORTH);

        JPanel cardPanel = new JPanel();
        cardPanel.setBackground(cardColor);
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 64, 84), 1, true),
                new EmptyBorder(15, 15, 15, 15)
        ));
        cardPanel.setLayout(new GridLayout(3, 1, 0, 10));

        JLabel lblUsername = new JLabel("Nombre de Usuario (username):");
        lblUsername.setFont(labelFont);
        lblUsername.setForeground(fgColor);
        cardPanel.add(lblUsername);

        txtUsername = new JTextField();
        txtUsername.setBackground(inputColor);
        txtUsername.setForeground(fgColor);
        txtUsername.setCaretColor(fgColor);
        txtUsername.setFont(inputFont);
        txtUsername.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 94, 114), 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        cardPanel.add(txtUsername);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblStatus.setForeground(new Color(170, 180, 200));
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        cardPanel.add(lblStatus);

        contentPane.add(cardPanel, BorderLayout.CENTER);

        btnConnect = new JButton("Conectar 🐾");
        btnConnect.setBackground(accentColor);
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFont(buttonFont);
        btnConnect.setFocusPainted(false);
        btnConnect.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        btnConnect.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnConnect.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btnConnect.setBackground(btnHoverColor);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btnConnect.setBackground(accentColor);
            }
        });

        btnConnect.addActionListener(this::handleConnect);
        contentPane.add(btnConnect, BorderLayout.SOUTH);
    }

    private void handleConnect(ActionEvent e) {
        String username = txtUsername.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "El nombre de usuario no puede estar vacío.",
                    "Error de Entrada",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        txtUsername.setEnabled(false);
        btnConnect.setEnabled(false);
        lblStatus.setText("Conectando con el servidor...");

        SwingWorker<AuthResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AuthResult doInBackground() throws Exception {
                Connection conn = new Connection();
                conn.connect(host, port);

                byte[] userBytes = username.getBytes(StandardCharsets.UTF_8);
                byte[] payload = new byte[userBytes.length + 1];
                System.arraycopy(userBytes, 0, payload, 0, userBytes.length);
                payload[payload.length - 1] = 0; // null term

                Message header = new Message(payload.length, MessageType.AUTH, (short) 0, (short) 0xFFFF);
                conn.send(header, payload);

                Message respHeader = conn.readHeader();
                if (respHeader.type() != MessageType.AUTH) {
                    throw new IOException("Tipo de respuesta inválido de servidor: " + respHeader.type());
                }

                byte[] respPayload = conn.readPayload(respHeader.payloadLen());
                if (respPayload.length < 3) {
                    throw new IOException("Respuesta de servidor malformada.");
                }

                byte status = respPayload[0];
                short assignedId = (short) (((respPayload[1] & 0xFF) << 8) | (respPayload[2] & 0xFF));
                String message = new String(respPayload, 3, respPayload.length - 3, StandardCharsets.UTF_8);

                // Inicializar crypto E2E y registrar clave pública en el servidor
                CryptoManager crypto = null;
                try {
                    crypto = new CryptoManager();
                    byte[] pubKey = crypto.getPublicKeyBytes();
                    conn.send(new Message(pubKey.length, MessageType.DH_EXCHANGE,
                            assignedId, (short) 0xFFFF), pubKey);
                } catch (Exception ignored) {}

                return new AuthResult(status == 0, assignedId, message, conn, crypto);
            }

            @Override
            protected void done() {
                try {
                    AuthResult result = get();
                    if (result.success()) {
                        lblStatus.setText("¡Autenticado!");
                        ChatFrame chatFrame = new ChatFrame(result.connection(), result.userId(), username, result.crypto());
                        chatFrame.setVisible(true);
                        dispose();
                    } else {
                        showError("Inicio de sesión rechazado: " + result.message());
                        result.connection().close();
                    }
                } catch (Exception ex) {
                    String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    showError("Error de conexión: " + err);
                }
            }

            private void showError(String errorMsg) {
                lblStatus.setText(" ");
                txtUsername.setEnabled(true);
                btnConnect.setEnabled(true);
                JOptionPane.showMessageDialog(LoginFrame.this,
                        errorMsg,
                        "Fallo al Conectar",
                        JOptionPane.ERROR_MESSAGE);
            }
        };

        worker.execute();
    }

    private record AuthResult(boolean success, short userId, String message, Connection connection, CryptoManager crypto) {}
}
