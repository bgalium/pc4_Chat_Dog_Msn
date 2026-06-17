package uni.cc4p1.client.model;

public enum MessageType {

    AUTH(1), TEXT(2), FILE_START(3), FILE_CHUNK(4), FILE_END(5),
    GROUP(6), QR(7), SALES(8), METRICS(9), ERROR(10),
    DH_EXCHANGE(11);

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Tipo de mensaje desconocido: " + code);
    }
}
