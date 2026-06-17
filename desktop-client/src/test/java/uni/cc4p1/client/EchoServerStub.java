package uni.cc4p1.client;

import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageParser;
import uni.cc4p1.client.model.MessageType;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EchoServerStub {
    private static final int PORT = 8080;
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static short nextUserId = 1001;

    public static void main(String[] args) {
        System.out.println("=== Dog Messenger Echo Server Stub (Binary Protocol) ===");
        System.out.println("Iniciando en puerto " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[INFO] Servidor listo. Esperando conexiones...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[INFO] Nuevo cliente conectado desde: " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Error en el servidor: " + e.getMessage());
        }
    }

    private static void broadcast(Message header, byte[] payload) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(header, payload);
            }
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private short userId = 0;
        private String username = "Desconocido";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public synchronized void sendMessage(Message header, byte[] payload) {
            try {
                if (out != null) {
                    MessageParser.writeMessage(out, header, payload);
                }
            } catch (IOException e) {
                System.err.println("[ERROR] Error al enviar mensaje a " + username + ": " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                while (!socket.isClosed()) {
                    Message header = MessageParser.readHeader(in);
                    byte[] payload = null;
                    if (header.payloadLen() > 0) {
                        payload = new byte[header.payloadLen()];
                        in.readFully(payload);
                    }

                    if (header.type() == MessageType.AUTH) {
                        String rawUser = new String(payload, StandardCharsets.UTF_8);
                        int nullIdx = rawUser.indexOf('\0');
                        this.username = nullIdx != -1 ? rawUser.substring(0, nullIdx) : rawUser.trim();
                        this.userId = nextUserId++;
                        System.out.println("[CONNECT] Cliente " + username + " autenticado con ID " + userId);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        dos.writeByte(0); // Status = 0 (OK)
                        dos.writeShort(userId);
                        dos.write(("Hola " + username + ", ¡bienvenido al servidor de pruebas!").getBytes(StandardCharsets.UTF_8));
                        dos.flush();
                        byte[] respPayload = baos.toByteArray();

                        Message respHeader = new Message(respPayload.length, MessageType.AUTH, (short) 0, userId);
                        sendMessage(respHeader, respPayload);
                    } else if (header.type() == MessageType.TEXT) {
                        String text = payload != null ? new String(payload, StandardCharsets.UTF_8) : "";
                        System.out.println("[TEXTO] De " + username + " (#" + header.senderId() + ") -> " + text);
                        
                        Message forwardHeader = new Message(payload.length, MessageType.TEXT, header.senderId(), (short) 0xFFFF);
                        broadcast(forwardHeader, payload);
                    } else if (header.type() == MessageType.FILE_START || header.type() == MessageType.FILE_CHUNK || header.type() == MessageType.FILE_END) {
                        System.out.println("[ARCHIVO] Relay de " + header.type() + " (" + header.payloadLen() + " bytes) desde #" + header.senderId());
                        Message forwardHeader = new Message(header.payloadLen(), header.type(), header.senderId(), (short) 0xFFFF);
                        broadcast(forwardHeader, payload);
                    }
                }
            } catch (IOException e) {
                System.out.println("[INFO] Conexión terminada con " + username + " (" + e.getMessage() + ")");
            } finally {
                clients.remove(this);
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
