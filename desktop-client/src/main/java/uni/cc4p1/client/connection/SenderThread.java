package uni.cc4p1.client.connection;

import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageType;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Patrón Producer-Consumer: la UI produce mensajes vía queueMessage(),
 * este hilo los consume y envía por el socket.
 * BlockingQueue es thread-safe: no se necesita synchronized adicional.
 * Cifra TEXT con AES-256 si la clave E2E ya fue derivada.
 */
public class SenderThread extends Thread {

    private final Connection            connection;
    private final BlockingQueue<QueuedMessage> queue;
    private final CryptoManager         crypto;
    private volatile boolean            running = true;
    private final MessageListener       errorListener;

    public SenderThread(Connection connection, MessageListener errorListener, CryptoManager crypto) {
        super("dog-messenger-sender-thread");
        this.connection    = connection;
        this.queue         = new LinkedBlockingQueue<>();
        this.errorListener = errorListener;
        this.crypto        = crypto;
        setDaemon(true);
    }

    public void queueMessage(Message header, byte[] payload) {
        queue.offer(new QueuedMessage(header, payload));
    }

    @Override
    public void run() {
        while (running) {
            try {
                QueuedMessage msg     = queue.take();
                Message       header  = msg.header();
                byte[]        payload = msg.payload();

                // Cifrar TEXT si tenemos clave E2E para el destinatario
                if (header.type() == MessageType.TEXT && crypto != null
                        && crypto.hasKeyFor(header.receiverId())) {
                    try {
                        payload = crypto.encrypt(header.receiverId(), payload);
                        header  = new Message(payload.length, MessageType.TEXT,
                                              header.senderId(), header.receiverId());
                    } catch (Exception ignored) {}
                }

                if (connection.isConnected()) {
                    connection.send(header, payload);
                } else {
                    if (errorListener != null)
                        errorListener.onConnectionClosed("No se pudo enviar. Conexión cerrada.");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                if (running && errorListener != null) errorListener.onError(e);
                break;
            }
        }
    }

    public void shutdown() { running = false; this.interrupt(); }
}
