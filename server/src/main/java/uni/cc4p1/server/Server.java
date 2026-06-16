package uni.cc4p1.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class Server {

    public static void main(String[] args) {
        int port = loadPortFromConfig();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Dog Messenger Server iniciado en el puerto " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept(); // El hilo principal se bloquea aquí esperando
                System.out.println("Conexión aceptada desde: " + clientSocket.getInetAddress());

                // Levantamos un nuevo hilo para el cliente
                ClientHandler handler = new ClientHandler(clientSocket);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Error fatal en el servidor: " + e.getMessage());
        }
    }

    private static int loadPortFromConfig() {
        Properties props = new Properties();
        try (InputStream input = Server.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("No se encontró config.properties, usando puerto por defecto 8080");
                return 8080;
            }
            props.load(input);
            return Integer.parseInt(props.getProperty("server.port", "8080"));
        } catch (IOException ex) {
            return 8080;
        }
    }
}