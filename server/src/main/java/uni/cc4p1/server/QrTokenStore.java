package uni.cc4p1.server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacena tokens QR temporales (TTL = 120 segundos).
 * Cada token mapea a un userId para recuperar su historial.
 */
public class QrTokenStore {

    private static final QrTokenStore INSTANCE = new QrTokenStore();
    private static final long         TTL_MS   = 120_000;

    private record Entry(short userId, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    private QrTokenStore() {}

    public static QrTokenStore getInstance() { return INSTANCE; }

    public String generate(short userId) {
        String token = UUID.randomUUID().toString(); // 36 chars
        tokens.put(token, new Entry(userId, System.currentTimeMillis() + TTL_MS));
        System.out.println("[QR] Token generado para user=" + userId + ": " + token);
        return token;
    }

    /** Retorna userId si el token es válido, -1 si expiró o no existe. */
    public short redeem(String token) {
        Entry entry = tokens.remove(token);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) return -1;
        return entry.userId();
    }
}
