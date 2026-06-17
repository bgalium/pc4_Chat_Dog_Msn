package uni.cc4p1.client.model

data class Message(
    val payloadLen: Int,
    val type: MessageType,
    val senderId: Short,
    val receiverId: Short
) {
    override fun toString(): String =
        "Header [Payload: ${payloadLen}B, Type: $type, Sender: $senderId, Receiver: $receiverId]"
}
