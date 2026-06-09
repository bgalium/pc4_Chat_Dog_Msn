package uni.cc4p1.server;

import java.io.DataInputStream;
import java.io.IOException;

public class MessageParser {

    public static Message readHeader(DataInputStream in) throws IOException {
        // Lee exactamente los 9 bytes definidos en el protocolo
        int payloadLen = in.readInt();       // 4 bytes
        byte typeCode = in.readByte();       // 1 byte
        short senderId = in.readShort();     // 2 bytes
        short receiverId = in.readShort();   // 2 bytes

        MessageType type = MessageType.fromCode(typeCode);
        return new Message(payloadLen, type, senderId, receiverId);
    }
}