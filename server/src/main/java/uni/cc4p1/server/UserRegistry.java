package uni.cc4p1.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registro global de usuarios conectados.
 * ConcurrentHashMap: permite que múltiples ClientHandler lean/escriban
 * simultáneamente sin corromper la estructura interna.
 * AtomicInteger: garantiza IDs únicos aunque dos clientes se conecten al mismo tiempo.
 */
public class UserRegistry {

    private static final UserRegistry INSTANCE = new UserRegistry();

    private final ConcurrentHashMap<Short, ClientHandler> handlers    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Short>        usernames   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Short, byte[]>        dhPublicKeys = new ConcurrentHashMap<>();
    private final AtomicInteger                           idCounter   = new AtomicInteger(1);

    private UserRegistry() {}

    public static UserRegistry getInstance() { return INSTANCE; }

    /** Registra usuario. Retorna el id asignado, o -1 si el username ya existe. */
    public short register(String username, ClientHandler handler) {
        short newId = (short) idCounter.getAndIncrement();
        if (usernames.putIfAbsent(username, newId) != null) return -1;
        handlers.put(newId, handler);
        System.out.println("[Registry] + " + username + " id=" + newId
                + " | conectados=" + handlers.size());
        return newId;
    }

    public ClientHandler getHandler(short userId) {
        return handlers.get(userId);
    }

    public void remove(short userId, String username) {
        handlers.remove(userId);
        usernames.remove(username);
        System.out.println("[Registry] - " + username + " | conectados=" + handlers.size());
    }

    public boolean isOnline(short userId) {
        return handlers.containsKey(userId);
    }

    public int getOnlineCount() { return handlers.size(); }

    public Short getIdByUsername(String username) {
        return usernames.get(username);
    }

    public void storeDhKey(short userId, byte[] publicKey) { dhPublicKeys.put(userId, publicKey); }
    public byte[] getDhKey(short userId) { return dhPublicKeys.get(userId); }
}
