package uni.cc4p1.client.gui;

import uni.cc4p1.client.connection.SenderThread;
import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

public class SalesDialog extends JDialog {

    private static final byte SALES_REGISTER = 0x01;
    private static final byte SALES_ORDER    = 0x02;
    private static final byte SALES_QUERY    = 0x03;
    private static final byte SALES_REPORT   = 0x04;

    private final SenderThread sender;
    private final short        localUserId;

    public SalesDialog(JFrame parent, SenderThread sender, short localUserId) {
        super(parent, "💰 Nodo de Ventas", true);
        this.sender      = sender;
        this.localUserId = localUserId;

        Color bg   = new Color(37, 40, 52);
        Color fg   = new Color(220, 224, 232);
        Color inp  = new Color(48, 52, 68);
        Font  ui   = new Font("Segoe UI", Font.PLAIN, 13);
        Font  bold = new Font("Segoe UI", Font.BOLD,  13);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(bg); tabs.setForeground(fg); tabs.setFont(bold);

        tabs.addTab("Registrar",  buildRegisterPanel(bg, fg, inp, ui, bold));
        tabs.addTab("Pedido",     buildOrderPanel(bg, fg, inp, ui, bold));
        tabs.addTab("Consultar",  buildQueryPanel(bg, fg, inp, ui, bold));
        tabs.addTab("Reporte",    buildReportPanel(bg, fg, ui, bold));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(tabs, BorderLayout.CENTER);

        setContentPane(root);
        setSize(500, 420);
        setLocationRelativeTo(parent);
        setResizable(true);
    }

    // ── Tab 1: Registrar cliente ──────────────────────────────────────────────
    private JPanel buildRegisterPanel(Color bg, Color fg, Color inp, Font ui, Font bold) {
        JPanel p = panel(bg);
        p.setLayout(new GridBagLayout());
        GridBagConstraints c = gbc();

        JTextField txtName    = field(inp, fg, ui);
        JTextField txtContact = field(inp, fg, ui);

        c.gridy = 0; p.add(label("Nombre:", fg, bold), c);
        c.gridy = 1; p.add(txtName, c);
        c.gridy = 2; p.add(label("Contacto:", fg, bold), c);
        c.gridy = 3; p.add(txtContact, c);
        c.gridy = 4;
        JButton btn = accentBtn("Registrar", ui);
        btn.addActionListener(e -> {
            String name = txtName.getText().trim();
            String cont = txtContact.getText().trim();
            if (name.isEmpty()) { JOptionPane.showMessageDialog(this, "Ingresa un nombre."); return; }
            sendSales(SALES_REGISTER, buildRegisterPayload(name, cont));
            JOptionPane.showMessageDialog(this, "Registro enviado al nodo de ventas.");
        });
        p.add(btn, c);
        return p;
    }

    // ── Tab 2: Crear pedido ───────────────────────────────────────────────────
    private JPanel buildOrderPanel(Color bg, Color fg, Color inp, Font ui, Font bold) {
        JPanel p = panel(bg);
        p.setLayout(new GridBagLayout());
        GridBagConstraints c = gbc();

        JTextField txtClientId = field(inp, fg, ui);
        JTextField txtItem     = field(inp, fg, ui);
        JTextField txtQty      = field(inp, fg, ui);
        JTextField txtPrice    = field(inp, fg, ui);

        c.gridy = 0; p.add(label("Client ID:", fg, bold), c);
        c.gridy = 1; p.add(txtClientId, c);
        c.gridy = 2; p.add(label("Ítem:", fg, bold), c);
        c.gridy = 3; p.add(txtItem, c);
        c.gridy = 4; p.add(label("Cantidad:", fg, bold), c);
        c.gridy = 5; p.add(txtQty, c);
        c.gridy = 6; p.add(label("Precio (soles):", fg, bold), c);
        c.gridy = 7; p.add(txtPrice, c);
        c.gridy = 8;
        JButton btn = accentBtn("Enviar pedido", ui);
        btn.addActionListener(e -> {
            try {
                short cid   = Short.parseShort(txtClientId.getText().trim());
                String item = txtItem.getText().trim();
                int qty     = Integer.parseInt(txtQty.getText().trim());
                double price= Double.parseDouble(txtPrice.getText().trim());
                long cents  = Math.round(price * 100);
                sendSales(SALES_ORDER, buildOrderPayload(cid, item, qty, cents));
                JOptionPane.showMessageDialog(this, "Pedido enviado.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Verifica los campos numéricos.");
            }
        });
        p.add(btn, c);
        return p;
    }

    // ── Tab 3: Consultar pedidos ──────────────────────────────────────────────
    private JPanel buildQueryPanel(Color bg, Color fg, Color inp, Font ui, Font bold) {
        JPanel p = panel(bg);
        p.setLayout(new GridBagLayout());
        GridBagConstraints c = gbc();

        JTextField txtClientId = field(inp, fg, ui);

        c.gridy = 0; p.add(label("Client ID:", fg, bold), c);
        c.gridy = 1; p.add(txtClientId, c);
        c.gridy = 2;
        JButton btn = accentBtn("Consultar", ui);
        btn.addActionListener(e -> {
            try {
                short cid = Short.parseShort(txtClientId.getText().trim());
                sendSales(SALES_QUERY, buildShortPayload(cid));
                JOptionPane.showMessageDialog(this, "Consulta enviada. Ver log del chat.");
                dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Client ID inválido.");
            }
        });
        p.add(btn, c);
        return p;
    }

    // ── Tab 4: Reporte global ─────────────────────────────────────────────────
    private JPanel buildReportPanel(Color bg, Color fg, Font ui, Font bold) {
        JPanel p = panel(bg);
        p.setLayout(new GridBagLayout());
        GridBagConstraints c = gbc();

        c.gridy = 0;
        JLabel lbl = new JLabel("Solicita el reporte total del nodo de ventas.");
        lbl.setFont(ui); lbl.setForeground(fg);
        p.add(lbl, c);
        c.gridy = 1;
        JButton btn = accentBtn("Obtener reporte", ui);
        btn.addActionListener(e -> {
            sendSales(SALES_REPORT, new byte[0]);
            JOptionPane.showMessageDialog(this, "Reporte solicitado. Ver log del chat.");
            dispose();
        });
        p.add(btn, c);
        return p;
    }

    // ── Helpers de payload ────────────────────────────────────────────────────

    private byte[] buildRegisterPayload(String name, String contact) {
        byte[] nb = name.getBytes(StandardCharsets.UTF_8);
        byte[] cb = contact.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[nb.length + 1 + cb.length];
        System.arraycopy(nb, 0, out, 0, nb.length);
        out[nb.length] = 0;
        System.arraycopy(cb, 0, out, nb.length + 1, cb.length);
        return out;
    }

    private byte[] buildOrderPayload(short clientId, String item, int qty, long priceCents) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeShort(clientId);
            byte[] ib = item.getBytes(StandardCharsets.UTF_8);
            dos.write(ib);
            dos.write(0);
            dos.writeInt(qty);
            dos.writeLong(priceCents);
            dos.flush();
            return baos.toByteArray();
        } catch (Exception ex) { return new byte[0]; }
    }

    private byte[] buildShortPayload(short value) {
        return new byte[]{(byte)(value >> 8), (byte)(value & 0xFF)};
    }

    private void sendSales(byte subCmd, byte[] data) {
        byte[] payload = new byte[1 + data.length];
        payload[0] = subCmd;
        System.arraycopy(data, 0, payload, 1, data.length);
        sender.queueMessage(
            new Message(payload.length, MessageType.SALES, localUserId, (short) 0xFFFF),
            payload
        );
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JPanel panel(Color bg) {
        JPanel p = new JPanel();
        p.setBackground(bg);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        return p;
    }

    private GridBagConstraints gbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 4, 4);
        c.weightx = 1; c.gridx = 0;
        return c;
    }

    private JLabel label(String text, Color fg, Font font) {
        JLabel l = new JLabel(text);
        l.setForeground(fg); l.setFont(font);
        return l;
    }

    private JTextField field(Color bg, Color fg, Font font) {
        JTextField f = new JTextField();
        f.setBackground(bg); f.setForeground(fg); f.setCaretColor(fg); f.setFont(font);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(90, 94, 114), 1, true),
            new EmptyBorder(4, 8, 4, 8)));
        f.setPreferredSize(new Dimension(420, 32));
        return f;
    }

    private JButton accentBtn(String text, Font font) {
        JButton b = new JButton(text);
        b.setBackground(new Color(92, 124, 250));
        b.setForeground(Color.WHITE);
        b.setFont(font); b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(70, 100, 220), 1, true));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }
}
