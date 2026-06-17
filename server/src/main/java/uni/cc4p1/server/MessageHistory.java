package uni.cc4p1.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Historial de mensajes de texto por usuario (últimos 100).
 * Necesario para la clonación por QR.
 */
public class MessageHistory {

    private static final MessageHistory INSTANCE    = new MessageHistory();
    private static final int            MAX_HISTORY = 100;

    private final ConcurrentHashMap<Short, List<String>> history = new ConcurrentHashMap<>();

    private MessageHistory() {}

    public static MessageHistory getInstance() { return INSTANCE; }

    public synchronized void store(short senderId, short receiverId, String text) {
        addTo(senderId, senderId, receiverId, text);
        addTo(receiverId, senderId, receiverId, text);
    }

    private void addTo(short owner, short from, short to, String text) {
        List<String> msgs = history.computeIfAbsent(owner, k -> new ArrayList<>());
        String entry = "{\"from\":" + Short.toUnsignedInt(from)
                + ",\"to\":" + Short.toUnsignedInt(to)
                + ",\"text\":\"" + escape(text) + "\"}";
        msgs.add(entry);
        if (msgs.size() > MAX_HISTORY) msgs.remove(0);
    }

    public String toJson(short userId) {
        List<String> msgs = history.getOrDefault(userId, Collections.emptyList());
        synchronized (this) {
            return "[" + String.join(",", msgs) + "]";
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
