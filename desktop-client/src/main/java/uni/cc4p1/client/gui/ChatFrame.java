package uni.cc4p1.client.gui;

import uni.cc4p1.client.connection.Connection;
import uni.cc4p1.client.connection.CryptoManager;
import uni.cc4p1.client.connection.MessageListener;
import uni.cc4p1.client.connection.ReceiverThread;
import uni.cc4p1.client.connection.SenderThread;
import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatFrame extends JFrame {

    private static final byte QR_REQUEST = 0x01;
    private static final byte QR_REDEEM  = 0x02;

    private final Connection    connection;
    private final short         localUserId;
    private final String        username;
    private final CryptoManager crypto;
    private ReceiverThread      receiver;   // no final: se asigna después de los lambdas
    private SenderThread        sender;

    private final JTextArea  txtChatLog;
    private final JTextField txtInput;
    private final JTextField txtRecipient;
    private final JButton    btnAttach;

    private java.io.ByteArrayOutputStream activeDownloadStream;
    private String                         activeDownloadFilename;

    public ChatFrame(Connection connection, short localUserId, String username, CryptoManager crypto) {
        this.connection  = connection;
        this.localUserId = localUserId;
        this.username    = username;
        this.crypto      = crypto;

        setTitle("Dog Messenger 🐾 — " + username + " (#" + localUserId + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(660, 540);
        setLocationRelativeTo(null);

        Color bgColor    = new Color(28, 30, 38);
        Color cardColor  = new Color(37, 40, 52);
        Color fgColor    = new Color(220, 224, 232);
        Color accentColor= new Color(92, 124, 250);
        Color inputColor = new Color(48, 52, 68);
        Font  logFont    = new Font("Monospaced", Font.PLAIN, 13);
        Font  uiFont     = new Font("Segoe UI",   Font.PLAIN, 14);
        Font  boldFont   = new Font("Segoe UI",   Font.BOLD,  14);

        JPanel contentPane = new JPanel(new BorderLayout(0, 10));
        contentPane.setBackground(bgColor);
        contentPane.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(contentPane);

        // ── Header ────────────────────────────────────────────────────────
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(cardColor);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 64, 84), 1, true),
                new EmptyBorder(10, 15, 10, 15)));
        JLabel lblInfo = new JLabel("🐾 " + username + "   |   ID: #" + localUserId);
        lblInfo.setFont(boldFont);
        lblInfo.setForeground(Color.WHITE);
        headerPanel.add(lblInfo, BorderLayout.WEST);

        JPanel recipientPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        recipientPanel.setBackground(cardColor);
        JLabel lblRec = new JLabel("Para (ID):");
        lblRec.setFont(boldFont);
        lblRec.setForeground(fgColor);
        recipientPanel.add(lblRec);
        txtRecipient = new JTextField("FFFF", 6);
        txtRecipient.setBackground(inputColor);
        txtRecipient.setForeground(fgColor);
        txtRecipient.setCaretColor(fgColor);
        txtRecipient.setFont(uiFont);
        txtRecipient.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 94, 114), 1, true),
                new EmptyBorder(3, 5, 3, 5)));
        txtRecipient.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { requestDhIfNeeded(); }
        });
        recipientPanel.add(txtRecipient);
        headerPanel.add(recipientPanel, BorderLayout.EAST);
        contentPane.add(headerPanel, BorderLayout.NORTH);

        // ── Chat log ──────────────────────────────────────────────────────
        txtChatLog = new JTextArea();
        txtChatLog.setBackground(cardColor);
        txtChatLog.setForeground(fgColor);
        txtChatLog.setFont(logFont);
        txtChatLog.setEditable(false);
        txtChatLog.setLineWrap(true);
        txtChatLog.setWrapStyleWord(true);
        txtChatLog.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(txtChatLog);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(60, 64, 84), 1, true));
        contentPane.add(scroll, BorderLayout.CENTER);

        // ── Footer ────────────────────────────────────────────────────────
        JPanel footerPanel = new JPanel(new BorderLayout(10, 0));
        footerPanel.setBackground(bgColor);
        txtInput = new JTextField();
        txtInput.setBackground(inputColor);
        txtInput.setForeground(fgColor);
        txtInput.setCaretColor(fgColor);
        txtInput.setFont(uiFont);
        txtInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 94, 114), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        txtInput.addActionListener(e -> sendMessage());
        footerPanel.add(txtInput, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new GridLayout(1, 4, 6, 0));
        actionPanel.setBackground(bgColor);
        btnAttach = darkBtn("📎 Archivo", cardColor, fgColor, boldFont);
        btnAttach.addActionListener(e -> selectAndSendFile());
        actionPanel.add(btnAttach);
        JButton btnGroups = darkBtn("👥 Grupos", cardColor, fgColor, boldFont);
        btnGroups.addActionListener(e -> new GroupsDialog(this, sender, localUserId).setVisible(true));
        actionPanel.add(btnGroups);
        JButton btnQR = darkBtn("📷 QR", cardColor, fgColor, boldFont);
        btnQR.addActionListener(e -> requestQrToken());
        actionPanel.add(btnQR);
        JButton btnSend = darkBtn("Enviar 🐾", accentColor, Color.WHITE, boldFont);
        btnSend.addActionListener(e -> sendMessage());
        actionPanel.add(btnSend);
        footerPanel.add(actionPanel, BorderLayout.EAST);
        contentPane.add(footerPanel, BorderLayout.SOUTH);

        // ── Listener ─────────────────────────────────────────────────────
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
        MessageListener listener = new MessageListener() {
            @Override public void onMessageReceived(Message h, byte[] p) {
                String t = timeFmt.format(new Date());
                SwingUtilities.invokeLater(() -> {
                    try { dispatchIncoming(h, p, t); }
                    catch (Exception ex) { appendLog("[ERROR] " + ex.getMessage()); }
                });
            }
            @Override public void onConnectionClosed(String reason) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(ChatFrame.this,
                            "Conexión cerrada: " + reason, "Desconectado",
                            JOptionPane.INFORMATION_MESSAGE);
                    cleanupAndExit();
                });
            }
            @Override public void onError(Exception e) {
                SwingUtilities.invokeLater(() -> appendLog("[ERROR] " + e.getMessage()));
            }
        };

        receiver = new ReceiverThread(connection, listener, crypto);
        sender   = new SenderThread(connection, listener, crypto);
        receiver.start();
        sender.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cleanupAndExit(); }
        });

        appendLog("🐾 Bienvenido, " + username + "!");
        appendLog("[INFO] Encriptación E2E activa al escribir el ID del destinatario.");
        appendLog("──────────────────────────────────────────────────────────");
    }

    // ─── Dispatch de mensajes entrantes ──────────────────────────────────────

    private void dispatchIncoming(Message h, byte[] p, String t) throws Exception {
        switch (h.type()) {
            case TEXT -> {
                String text = p != null ? new String(p, StandardCharsets.UTF_8) : "";
                String who  = h.senderId() == localUserId ? "Yo"
                            : h.senderId() == 0 ? "Servidor" : "Usuario #" + h.senderId();
                String lock = crypto != null && crypto.hasKeyFor(h.senderId()) ? " 🔒" : "";
                appendLog(String.format("[%s] %s%s: %s", t, who, lock, text));
            }
            case FILE_START -> {
                java.io.DataInputStream dis = new java.io.DataInputStream(
                        new java.io.ByteArrayInputStream(p));
                short fnLen = dis.readShort();
                byte[] fnB  = new byte[fnLen]; dis.readFully(fnB);
                long   sz   = dis.readLong();
                activeDownloadFilename = new String(fnB, StandardCharsets.UTF_8);
                activeDownloadStream   = new java.io.ByteArrayOutputStream();
                appendLog(String.format("[%s] [ARCHIVO] Descargando: %s (%d B) de #%d",
                        t, activeDownloadFilename, sz, h.senderId()));
            }
            case FILE_CHUNK -> {
                txtChatLog.append(".");
                if (activeDownloadStream != null && p != null)
                    activeDownloadStream.write(p, 0, p.length);
            }
            case FILE_END -> {
                String sha = p != null ? new String(p, StandardCharsets.US_ASCII) : "";
                appendLog(String.format("\n[%s] [ARCHIVO] Completo. SHA-256: %s", t, sha));
                if (activeDownloadStream != null && activeDownloadFilename != null) {
                    File dir = new File("downloads");
                    if (!dir.exists()) dir.mkdir();
                    File dest = new File(dir, "recv_" + activeDownloadFilename);
                    Files.write(dest.toPath(), activeDownloadStream.toByteArray());
                    appendLog("[SISTEMA] Guardado: " + dest.getAbsolutePath());
                    activeDownloadStream = null; activeDownloadFilename = null;
                }
            }
            case GROUP -> {
                if (p == null || p.length == 0) return;
                byte cmd = p[0];
                if (cmd == 0x01 || cmd == 0x02) {
                    byte s = p[1];
                    short gid = (short) (((p[2] & 0xFF) << 8) | (p[3] & 0xFF));
                    String msg = new String(p, 4, p.length - 4, StandardCharsets.UTF_8);
                    appendLog(String.format("[%s] [GRUPO] %s (gid=%d)", t,
                            s == 0 ? msg : "Error: " + msg, Short.toUnsignedInt(gid)));
                } else if (cmd == 0x03) {
                    short gid  = (short) (((p[1] & 0xFF) << 8) | (p[2] & 0xFF));
                    short from = (short) (((p[3] & 0xFF) << 8) | (p[4] & 0xFF));
                    String msg = new String(p, 5, p.length - 5, StandardCharsets.UTF_8);
                    appendLog(String.format("[%s] [Grupo #%d] #%d: %s",
                            t, Short.toUnsignedInt(gid), Short.toUnsignedInt(from), msg));
                } else if (cmd == 0x04) {
                    String json = new String(p, 1, p.length - 1, StandardCharsets.UTF_8);
                    JOptionPane.showMessageDialog(this, json, "Grupos", JOptionPane.INFORMATION_MESSAGE);
                }
            }
            case QR -> {
                if (p == null || p.length == 0) return;
                if (p[0] == QR_REQUEST) {
                    String token = new String(p, 1, p.length - 1, StandardCharsets.US_ASCII);
                    appendLog("[QR] Token listo. Muestra el código al otro dispositivo.");
                    new QRFrame(this, token).setVisible(true);
                } else if (p[0] == QR_REDEEM) {
                    String content = new String(p, 2, p.length - 2, StandardCharsets.UTF_8);
                    appendLog(p[1] == 0 ? "[QR] Historial recibido:\n" + content : "[QR] Error: " + content);
                }
            }
            case METRICS -> {
                String json = p != null ? new String(p, StandardCharsets.UTF_8) : "{}";
                JOptionPane.showMessageDialog(this,
                        prettyJson(json), "Métricas del servidor", JOptionPane.INFORMATION_MESSAGE);
            }
            case ERROR -> {
                int code = p != null && p.length > 0 ? p[0] : -1;
                String msg = p != null && p.length > 1
                        ? new String(p, 1, p.length - 1, StandardCharsets.UTF_8) : "?";
                appendLog(String.format("[%s] [ERROR %d] %s", t, code, msg));
            }
            default -> appendLog(String.format("[%s] [SISTEMA] Tipo %s de #%d", t, h.type(), h.senderId()));
        }
    }

    // ─── Acciones del usuario ─────────────────────────────────────────────────

    private void sendMessage() {
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;
        txtInput.setText("");
        short  recId   = parseRecipientId();
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        sender.queueMessage(new Message(payload.length, MessageType.TEXT, localUserId, recId), payload);
        String lock = crypto != null && crypto.hasKeyFor(recId) ? " 🔒" : "";
        appendLog("[" + new SimpleDateFormat("HH:mm").format(new Date()) + "] Yo" + lock + ": " + text);
    }

    private void requestQrToken() {
        sender.queueMessage(new Message(1, MessageType.QR, localUserId, (short) 0xFFFF),
                new byte[]{QR_REQUEST});
        appendLog("[QR] Solicitando token...");
    }

    private void requestDhIfNeeded() {
        if (crypto == null) return;
        short recId = parseRecipientId();
        if (recId == (short) 0xFFFF) return;
        if (!crypto.hasKeyFor(recId)) {
            sender.queueMessage(new Message(0, MessageType.DH_EXCHANGE, localUserId, recId), new byte[0]);
        }
    }

    private void selectAndSendFile() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            sendFileAsync(fc.getSelectedFile(), parseRecipientId());
    }

    private void sendFileAsync(File file, short recId) {
        btnAttach.setEnabled(false);
        appendLog("[ARCHIVO] Preparando: " + file.getName());
        new Thread(() -> {
            try {
                byte[] fn    = file.getName().getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream      dos  = new DataOutputStream(baos);
                dos.writeShort((short) fn.length);
                dos.write(fn);
                dos.writeLong(file.length());
                dos.flush();
                sender.queueMessage(new Message(baos.size(), MessageType.FILE_START, localUserId, recId),
                        baos.toByteArray());

                byte[] bytes = Files.readAllBytes(file.toPath());
                int off = 0, n = 0;
                while (off < bytes.length) {
                    int len = Math.min(4096, bytes.length - off);
                    byte[] chunk = new byte[len];
                    System.arraycopy(bytes, off, chunk, 0, len);
                    sender.queueMessage(new Message(len, MessageType.FILE_CHUNK, localUserId, recId), chunk);
                    off += len; n++;
                    if (n % 5 == 0) SwingUtilities.invokeLater(() -> txtChatLog.append("."));
                }
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                StringBuilder sb = new StringBuilder();
                for (byte b : md.digest(bytes)) sb.append(String.format("%02x", b));
                byte[] ep = sb.toString().getBytes(StandardCharsets.US_ASCII);
                sender.queueMessage(new Message(ep.length, MessageType.FILE_END, localUserId, recId), ep);
                final int fn2 = n; final String hash = sb.toString();
                SwingUtilities.invokeLater(() -> {
                    appendLog(String.format("\n[ARCHIVO] '%s' enviado (%d chunks). SHA-256: %s",
                            file.getName(), fn2, hash));
                    btnAttach.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { appendLog("[ERROR] " + e.getMessage()); btnAttach.setEnabled(true); });
            }
        }, "file-sender").start();
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private short parseRecipientId() {
        String s = txtRecipient.getText().trim();
        try {
            if (s.equalsIgnoreCase("FFFF")) return (short) 0xFFFF;
            if (s.toLowerCase().startsWith("0x")) return (short) Integer.parseInt(s.substring(2), 16);
            return (short) Integer.parseInt(s);
        } catch (NumberFormatException e) { return (short) 0xFFFF; }
    }

    private void appendLog(String msg) {
        txtChatLog.append(msg + "\n");
        txtChatLog.setCaretPosition(txtChatLog.getDocument().getLength());
    }

    private String prettyJson(String json) {
        return json.replace(",\"", ",\n  \"").replace("{", "{\n  ").replace("}", "\n}");
    }

    private JButton darkBtn(String text, Color bg, Color fg, Font font) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(fg); b.setFont(font);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(90, 94, 114), 1, true));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void cleanupAndExit() {
        if (receiver != null) receiver.shutdown();
        if (sender   != null) sender.shutdown();
        if (connection != null) connection.close();
        System.exit(0);
    }
}
