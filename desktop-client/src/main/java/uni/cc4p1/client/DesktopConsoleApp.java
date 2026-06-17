package uni.cc4p1.client;

import uni.cc4p1.client.connection.Connection;
import uni.cc4p1.client.connection.MessageListener;
import uni.cc4p1.client.connection.ReceiverThread;
import uni.cc4p1.client.connection.SenderThread;
import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageType;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class DesktopConsoleApp {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private static Connection connection;
    private static ReceiverThread receiver;
    private static SenderThread sender;
    private static short localUserId = 0;
    private static String username;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido. Usando: " + DEFAULT_PORT);
            }
        }

        System.out.println("=== Dog Messenger Desktop Client (Consola) ===");
        Scanner scanner = new Scanner(System.in, "UTF-8");

        System.out.print("Ingrese su nombre de usuario (username): ");
        username = scanner.nextLine().trim();
        while (username.isEmpty()) {
            System.out.print("El nombre de usuario no puede estar vacío: ");
            username = scanner.nextLine().trim();
        }

        connection = new Connection();
        try {
            System.out.println("Conectando a " + host + ":" + port + "...");
            connection.connect(host, port);
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo conectar al servidor: " + e.getMessage());
            return;
        }

        try {
            if (!performAuthentication(scanner)) {
                connection.close();
                return;
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Fallo en la autenticación: " + e.getMessage());
            connection.close();
            return;
        }

        MessageListener listener = new MessageListener() {
            private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

            @Override
            public void onMessageReceived(Message header, byte[] payload) {
                String timeStr = timeFormat.format(new Date());
                try {
                    if (header.type() == MessageType.ERROR) {
                        int errorCode = payload != null && payload.length > 0 ? payload[0] : -1;
                        String errMsg = payload != null && payload.length > 1 ? new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8) : "Desconocido";
                        System.err.println("\n[" + timeStr + "] [ERROR " + errorCode + "] " + errMsg);
                    } else if (header.type() == MessageType.TEXT) {
                        String text = payload != null ? new String(payload, StandardCharsets.UTF_8) : "";
                        String senderName = header.senderId() == localUserId ? "Yo" : ("Usuario #" + header.senderId());
                        System.out.println("\n[" + timeStr + "] " + senderName + ": " + text);
                    } else if (header.type() == MessageType.FILE_START) {
                        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload));
                        short fnLen = dis.readShort();
                        byte[] fnBytes = new byte[fnLen];
                        dis.readFully(fnBytes);
                        String filename = new String(fnBytes, StandardCharsets.UTF_8);
                        long size = dis.readLong();
                        System.out.println("\n[" + timeStr + "] [ARCHIVO] Iniciando descarga de: " + filename + " (" + size + " bytes) de Usuario #" + header.senderId());
                    } else if (header.type() == MessageType.FILE_CHUNK) {
                        System.out.print(".");
                    } else if (header.type() == MessageType.FILE_END) {
                        String sha256 = payload != null ? new String(payload, StandardCharsets.US_ASCII) : "";
                        System.out.println("\n[" + timeStr + "] [ARCHIVO] Descarga completada. Checksum SHA-256: " + sha256);
                    } else {
                        System.out.println("\n[" + timeStr + "] Mensaje recibido de tipo: " + header.type() + " de Emisor #" + header.senderId());
                    }
                } catch (Exception e) {
                    System.err.println("\n[ERROR] Error procesando paquete recibido: " + e.getMessage());
                }
                System.out.print(username + " > ");
            }

            @Override
            public void onConnectionClosed(String reason) {
                System.out.println("\n[INFO] Conexión terminada con el servidor: " + reason);
                cleanupAndExit();
            }

            @Override
            public void onError(Exception e) {
                System.err.println("\n[ERROR] Error de comunicación: " + e.getMessage());
            }
        };

        receiver = new ReceiverThread(connection, listener, null);
        sender = new SenderThread(connection, listener, null);

        receiver.start();
        sender.start();

        System.out.println("Escriba su mensaje y presione ENTER para enviar. Use ':file <ruta>' para enviar archivos. Use ':q' para salir.");
        System.out.print(username + " > ");

        while (connection.isConnected()) {
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.equals(":q")) break;
            if (line.isEmpty()) {
                System.out.print(username + " > ");
                continue;
            }

            if (line.startsWith(":file ")) {
                sendFile(line.substring(6).trim());
            } else {
                byte[] payload = line.getBytes(StandardCharsets.UTF_8);
                Message header = new Message(payload.length, MessageType.TEXT, localUserId, (short) 0xFFFF);
                sender.queueMessage(header, payload);
            }
            System.out.print(username + " > ");
        }

        cleanupAndExit();
    }

    private static boolean performAuthentication(Scanner scanner) throws IOException {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[usernameBytes.length + 1];
        System.arraycopy(usernameBytes, 0, payload, 0, usernameBytes.length);
        payload[payload.length - 1] = 0; // Null term

        Message header = new Message(payload.length, MessageType.AUTH, (short) 0, (short) 0xFFFF);
        connection.send(header, payload);

        System.out.println("Esperando respuesta de autenticación...");
        Message respHeader = connection.readHeader();
        if (respHeader.type() != MessageType.AUTH) {
            System.err.println("[ERROR] Tipo de respuesta inesperada: " + respHeader.type());
            return false;
        }

        byte[] respPayload = connection.readPayload(respHeader.payloadLen());
        if (respPayload.length < 3) {
            System.err.println("[ERROR] Respuesta de autenticación malformada.");
            return false;
        }

        byte status = respPayload[0];
        short assignedId = (short) (((respPayload[1] & 0xFF) << 8) | (respPayload[2] & 0xFF));
        String message = new String(respPayload, 3, respPayload.length - 3, StandardCharsets.UTF_8);

        if (status == 0) {
            localUserId = assignedId;
            System.out.println("[OK] Autenticado exitosamente. Tu ID asignado es: " + localUserId);
            System.out.println("Servidor: " + message);
            return true;
        } else {
            System.err.println("[RECHAZADO] Error de autenticación: " + message);
            return false;
        }
    }

    private static void sendFile(String pathStr) {
        File file = new File(pathStr);
        if (!file.exists() || !file.isFile()) {
            System.out.println("[SISTEMA] Archivo no encontrado: " + pathStr);
            return;
        }

        new Thread(() -> {
            try {
                String filename = file.getName();
                byte[] fnBytes = filename.getBytes(StandardCharsets.UTF_8);
                long fileSize = file.length();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.writeShort((short) fnBytes.length);
                dos.write(fnBytes);
                dos.writeLong(fileSize);
                dos.flush();
                byte[] startPayload = baos.toByteArray();
                
                Message startHeader = new Message(startPayload.length, MessageType.FILE_START, localUserId, (short) 0xFFFF);
                sender.queueMessage(startHeader, startPayload);
                System.out.println("\n[ARCHIVO] Enviando inicio de transferencia para: " + filename);

                byte[] fileBytes = Files.readAllBytes(file.toPath());
                int offset = 0;
                int chunkCount = 0;
                while (offset < fileBytes.length) {
                    int length = Math.min(4096, fileBytes.length - offset);
                    byte[] chunk = new byte[length];
                    System.arraycopy(fileBytes, offset, chunk, 0, length);
                    
                    Message chunkHeader = new Message(length, MessageType.FILE_CHUNK, localUserId, (short) 0xFFFF);
                    sender.queueMessage(chunkHeader, chunk);
                    
                    offset += length;
                    chunkCount++;
                    if (chunkCount % 5 == 0) System.out.print(".");
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(fileBytes);
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                byte[] endPayload = sb.toString().getBytes(StandardCharsets.US_ASCII);
                
                Message endHeader = new Message(endPayload.length, MessageType.FILE_END, localUserId, (short) 0xFFFF);
                sender.queueMessage(endHeader, endPayload);
                
                System.out.println("\n[ARCHIVO] Archivo enviado en " + chunkCount + " chunks. Hash SHA-256: " + sb.toString());
                System.out.print(username + " > ");
            } catch (Exception e) {
                System.err.println("\n[ERROR] Error al enviar el archivo: " + e.getMessage());
                System.out.print(username + " > ");
            }
        }).start();
    }

    private static void cleanup() {
        if (receiver != null) receiver.shutdown();
        if (sender != null) sender.shutdown();
        if (connection != null) connection.close();
    }

    private static void cleanupAndExit() {
        cleanup();
        System.exit(0);
    }
}
