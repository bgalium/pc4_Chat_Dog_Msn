package uni.cc4p1.server;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler extends Thread {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
            System.out.println("Nuevo cliente manejado por: " + Thread.currentThread().getName());

            // Loop para escuchar mensajes de este cliente continuamente
            while (!socket.isClosed()) {
                Message header = MessageParser.readHeader(in);
                System.out.println("Recibido -> " + header.toString());

                // (Placeholder) Aquí en el futuro se leerá el payload usando header.getPayloadLen()
                if (header.payloadLen() > 0) {
                    in.skipBytes(header.payloadLen()); // Ignoramos el payload por ahora
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + socket.getInetAddress());
        }
    }
}