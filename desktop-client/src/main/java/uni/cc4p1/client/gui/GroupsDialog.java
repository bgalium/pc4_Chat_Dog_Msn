package uni.cc4p1.client.gui;

import uni.cc4p1.client.connection.SenderThread;
import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Diálogo para gestión de grupos:
 *  - Crear grupo (nombre → servidor asigna ID)
 *  - Unirse a grupo (por ID)
 *  - Enviar mensaje a un grupo
 */
public class GroupsDialog extends JDialog {

    // Sub-comandos deben coincidir con ClientHandler en el servidor
    private static final byte GRP_CREATE  = 0x01;
    private static final byte GRP_JOIN    = 0x02;
    private static final byte GRP_MESSAGE = 0x03;
    private static final byte GRP_LIST    = 0x04;

    private final SenderThread sender;
    private final short        localUserId;

    public GroupsDialog(JFrame parent, SenderThread sender, short localUserId) {
        super(parent, "Grupos de Chat 🐾", false);
        this.sender      = sender;
        this.localUserId = localUserId;

        setSize(420, 360);
        setLocationRelativeTo(parent);

        Color bg    = new Color(28, 30, 38);
        Color card  = new Color(37, 40, 52);
        Color fg    = new Color(220, 224, 232);
        Color input = new Color(48, 52, 68);
        Font  font  = new Font("Segoe UI", Font.PLAIN, 13);
        Font  bold  = new Font("Segoe UI", Font.BOLD, 13);

        JPanel root = new JPanel(new GridLayout(3, 1, 0, 8));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // ── Crear grupo ──────────────────────────────────────────────
        JPanel pCreate = card(card, "Crear grupo");
        JTextField txtName = field(input, fg, font, "Nombre del grupo");
        JButton btnCreate = btn("Crear", new Color(92, 124, 250), bold);
        btnCreate.addActionListener(e -> {
            String name = txtName.getText().trim();
            if (name.isEmpty()) return;
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            byte[] payload   = new byte[1 + nameBytes.length];
            payload[0] = GRP_CREATE;
            System.arraycopy(nameBytes, 0, payload, 1, nameBytes.length);
            sender.queueMessage(new Message(payload.length, MessageType.GROUP, localUserId, (short) 0xFFFF), payload);
            txtName.setText("");
            JOptionPane.showMessageDialog(this, "Solicitud de creación enviada.");
        });
        pCreate.add(txtName);
        pCreate.add(btnCreate);
        root.add(pCreate);

        // ── Unirse a grupo ──────────────────────────────────────────
        JPanel pJoin = card(card, "Unirse a grupo");
        JTextField txtGid = field(input, fg, font, "ID del grupo (número)");
        JButton btnJoin = btn("Unirse", new Color(60, 179, 113), bold);
        btnJoin.addActionListener(e -> {
            try {
                short gid = Short.parseShort(txtGid.getText().trim());
                byte[] payload = new byte[3];
                payload[0] = GRP_JOIN;
                payload[1] = (byte) (gid >> 8);
                payload[2] = (byte) (gid & 0xFF);
                sender.queueMessage(new Message(3, MessageType.GROUP, localUserId, (short) 0xFFFF), payload);
                txtGid.setText("");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "ID de grupo inválido.");
            }
        });
        pJoin.add(txtGid);
        pJoin.add(btnJoin);
        root.add(pJoin);

        // ── Enviar mensaje a grupo ──────────────────────────────────
        JPanel pMsg = card(card, "Enviar mensaje al grupo");
        JPanel row  = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setBackground(card);
        JTextField txtMsgGid  = field(input, fg, font, "ID grupo");
        JTextField txtMsgText = field(input, fg, font, "Mensaje");
        row.add(txtMsgGid);
        row.add(txtMsgText);
        JButton btnSendGrp = btn("Enviar al grupo", new Color(92, 124, 250), bold);
        btnSendGrp.addActionListener(e -> {
            try {
                short  gid  = Short.parseShort(txtMsgGid.getText().trim());
                byte[] text = txtMsgText.getText().trim().getBytes(StandardCharsets.UTF_8);
                if (text.length == 0) return;
                byte[] payload = ByteBuffer.allocate(3 + text.length)
                        .put(GRP_MESSAGE)
                        .putShort(gid)
                        .put(text)
                        .array();
                sender.queueMessage(new Message(payload.length, MessageType.GROUP, localUserId, (short) 0xFFFF), payload);
                txtMsgText.setText("");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "ID de grupo inválido.");
            }
        });
        pMsg.add(row);
        pMsg.add(btnSendGrp);
        root.add(pMsg);

        setContentPane(root);
    }

    // ── helpers de UI ──────────────────────────────────────────────────────

    private JPanel card(Color bg, String title) {
        JPanel p = new JPanel(new GridLayout(3, 1, 0, 4));
        p.setBackground(bg);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 64, 84), 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        JLabel lbl = new JLabel(title);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        p.add(lbl);
        return p;
    }

    private JTextField field(Color bg, Color fg, Font font, String placeholder) {
        JTextField f = new JTextField();
        f.setBackground(bg);
        f.setForeground(fg);
        f.setCaretColor(fg);
        f.setFont(font);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 94, 114), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        f.putClientProperty("placeholder", placeholder);
        return f;
    }

    private JButton btn(String text, Color bg, Font font) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(font);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return b;
    }
}
