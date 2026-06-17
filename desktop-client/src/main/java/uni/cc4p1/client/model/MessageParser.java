package uni.cc4p1.client.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MessageParser {

    public static Message readHeader(DataInputStream in) throws IOException {
        int payloadLen = in.readInt();       // 4 bytes
        byte typeCode = in.readByte();       // 1 byte
        short senderId = in.readShort();     // 2 bytes
        short receiverId = in.readShort();   // 2 bytes

        MessageType type = MessageType.fromCode(typeCode);
        return new Message(payloadLen, type, senderId, receiverId);
    }

    public static void writeMessage(DataOutputStream out, Message header, byte[] payload) throws IOException {
        out.writeInt(payload != null ? payload.length : 0);
        out.writeByte(header.type().getCode());
        out.writeShort(header.senderId());
        out.writeShort(header.receiverId());
        if (payload != null && payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }
}
