package uni.cc4p1.client.gui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Ventana que muestra el QR de clonación.
 * El código QR contiene el token UUID que el otro dispositivo debe presentar al servidor.
 */
public class QRFrame extends JDialog {

    public QRFrame(JFrame parent, String token) {
        super(parent, "Clonar Chat — Escanea el QR", true);
        setSize(360, 430);
        setLocationRelativeTo(parent);
        setResizable(false);

        Color bg = new Color(28, 30, 38);
        Color fg = new Color(220, 224, 232);

        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(bg);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Escanea con el móvil para clonar el chat", SwingConstants.CENTER);
        title.setForeground(fg);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.add(title, BorderLayout.NORTH);

        // Generar QR
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix    matrix = writer.encode(token, BarcodeFormat.QR_CODE, 300, 300);
            BufferedImage img   = MatrixToImageWriter.toBufferedImage(matrix);
            JLabel qrLabel      = new JLabel(new ImageIcon(img));
            qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(qrLabel, BorderLayout.CENTER);
        } catch (WriterException e) {
            panel.add(new JLabel("Error generando QR: " + e.getMessage()), BorderLayout.CENTER);
        }

        // Token en texto para entrada manual
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBackground(bg);
        JLabel lblToken = new JLabel("Token manual:", SwingConstants.CENTER);
        lblToken.setForeground(fg);
        lblToken.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JTextField txtToken = new JTextField(token);
        txtToken.setEditable(false);
        txtToken.setHorizontalAlignment(JTextField.CENTER);
        txtToken.setBackground(new Color(48, 52, 68));
        txtToken.setForeground(fg);
        txtToken.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtToken.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JButton btnClose = new JButton("Cerrar");
        btnClose.addActionListener(e -> dispose());
        bottom.add(lblToken, BorderLayout.NORTH);
        bottom.add(txtToken, BorderLayout.CENTER);
        bottom.add(btnClose, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        setContentPane(panel);
    }
}
