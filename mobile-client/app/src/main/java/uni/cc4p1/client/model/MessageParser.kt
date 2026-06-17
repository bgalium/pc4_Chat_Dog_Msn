package uni.cc4p1.client.model

import java.io.DataInputStream
import java.io.DataOutputStream

object MessageParser {

    fun readHeader(input: DataInputStream): Message {
        val payloadLen = input.readInt()
        val typeCode = input.readByte().toInt() and 0xFF
        val senderId = input.readShort()
        val receiverId = input.readShort()
        val type = MessageType.fromCode(typeCode)
        return Message(payloadLen, type, senderId, receiverId)
    }

    fun writeMessage(output: DataOutputStream, header: Message, payload: ByteArray?) {
        output.writeInt(payload?.size ?: 0)
        output.writeByte(header.type.code)
        output.writeShort(header.senderId.toInt())
        output.writeShort(header.receiverId.toInt())
        if (payload != null && payload.isNotEmpty()) {
            output.write(payload)
        }
        output.flush()
    }
}
