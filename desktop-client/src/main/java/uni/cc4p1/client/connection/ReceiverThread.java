package uni.cc4p1.client.connection;

import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageType;

import java.io.IOException;

/**
 * Hilo daemon que escucha mensajes del servidor continuamente.
 * volatile boolean running: permite parada limpia sin synchronized.
 * Integra CryptoManager para descifrar TEXT E2E si la clave ya fue derivada.
 */
public class ReceiverThread extends Thread {

    private final Connection    connection;
    private final MessageListener listener;
    private final CryptoManager crypto;       // puede ser null si crypto no inicializado
    private volatile boolean    running = true;

    public ReceiverThread(Connection connection, MessageListener listener, CryptoManager crypto) {
        super("dog-messenger-receiver-thread");
        this.connection = connection;
        this.listener   = listener;
        this.crypto     = crypto;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (running && connection.isConnected()) {
            try {
                Message header  = connection.readHeader();
                byte[]  payload = header.payloadLen() > 0
                        ? connection.readPayload(header.payloadLen())
                        : new byte[0];

                // DH_EXCHANGE: derivar clave sin pasar al listener de UI
                if (header.type() == MessageType.DH_EXCHANGE && crypto != null && payload.length > 0) {
                    crypto.computeSharedKey(header.senderId(), payload);
                    continue;
                }

                // Descifrar TEXT si ya tenemos la clave del emisor
                if (header.type() == MessageType.TEXT && crypto != null
                        && crypto.hasKeyFor(header.senderId())) {
                    try {
                        payload = crypto.decrypt(header.senderId(), payload);
                    } catch (Exception ignored) {
                        // mensaje no cifrado o clave incorrecta → usar tal cual
                    }
                }

                if (listener != null) listener.onMessageReceived(header, payload);

            } catch (IOException e) {
                if (running) {
                    running = false;
                    if (listener != null) listener.onConnectionClosed(e.getMessage());
                }
                break;
            } catch (Exception e) {
                if (running && listener != null) listener.onError(e);
            }
        }
    }

    public void shutdown() { running = false; }
}
