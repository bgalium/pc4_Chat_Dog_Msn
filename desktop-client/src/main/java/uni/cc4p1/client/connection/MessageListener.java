package uni.cc4p1.client.connection;

import uni.cc4p1.client.model.Message;

public interface MessageListener {
    void onMessageReceived(Message header, byte[] payload);
    void onConnectionClosed(String reason);
    void onError(Exception e);
}
