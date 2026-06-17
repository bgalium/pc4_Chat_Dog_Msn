package uni.cc4p1.client.connection;

import uni.cc4p1.client.model.Message;
import uni.cc4p1.client.model.MessageParser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Connection {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private boolean connected = false;

    public synchronized void connect(String host, int port) throws IOException {
        if (connected) return;
        this.socket = new Socket(host, port);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.connected = true;
    }

    public synchronized void send(Message header, byte[] payload) throws IOException {
        if (!connected || socket == null || socket.isClosed()) {
            throw new IOException("Not connected to server.");
        }
        MessageParser.writeMessage(out, header, payload);
    }

    public Message readHeader() throws IOException {
        if (in == null) {
            throw new IOException("Not connected to server.");
        }
        return MessageParser.readHeader(in);
    }

    public byte[] readPayload(int len) throws IOException {
        if (in == null) {
            throw new IOException("Not connected to server.");
        }
        byte[] buffer = new byte[len];
        in.readFully(buffer);
        return buffer;
    }

    public synchronized boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public synchronized void close() {
        if (!connected) return;
        connected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
