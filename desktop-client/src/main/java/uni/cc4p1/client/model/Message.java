package uni.cc4p1.client.model;

public record Message(int payloadLen, MessageType type, short senderId, short receiverId) {

    @Override
    public String toString() {
        return String.format("Header [Payload: %d bytes, Tipo: %s, Emisor: %d, Receptor: %d]",
                payloadLen, type, senderId, receiverId);
    }
}
