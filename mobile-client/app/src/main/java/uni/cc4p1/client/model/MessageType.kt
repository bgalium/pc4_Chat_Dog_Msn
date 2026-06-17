package uni.cc4p1.client.model

enum class MessageType(val code: Int) {
    AUTH(1),
    TEXT(2),
    FILE_START(3),
    FILE_CHUNK(4),
    FILE_END(5),
    GROUP(6),
    QR(7),
    SALES(8),
    METRICS(9),
    ERROR(10),
    DH_EXCHANGE(11);

    companion object {
        fun fromCode(code: Int): MessageType =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown message type: $code")
    }
}
