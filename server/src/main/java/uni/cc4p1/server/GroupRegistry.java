package uni.cc4p1.server;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registro global de grupos de chat.
 * ConcurrentHashMap anidado: acceso concurrente seguro desde múltiples ClientHandlers.
 */
public class GroupRegistry {

    private static final GroupRegistry INSTANCE = new GroupRegistry();

    private final ConcurrentHashMap<Short, Set<Short>> members   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Short, String>     names     = new ConcurrentHashMap<>();
    private final AtomicInteger                        idCounter = new AtomicInteger(1);

    private GroupRegistry() {}

    public static GroupRegistry getInstance() { return INSTANCE; }

    public short create(String name, short creatorId) {
        short gid = (short) idCounter.getAndIncrement();
        Set<Short> m = Collections.newSetFromMap(new ConcurrentHashMap<>());
        m.add(creatorId);
        members.put(gid, m);
        names.put(gid, name);
        System.out.println("[Groups] Creado '" + name + "' gid=" + gid + " por user=" + creatorId);
        return gid;
    }

    public boolean join(short gid, short userId) {
        Set<Short> m = members.get(gid);
        if (m == null) return false;
        m.add(userId);
        System.out.println("[Groups] User " + userId + " unido a gid=" + gid);
        return true;
    }

    public Set<Short> getMembers(short gid) {
        return members.getOrDefault(gid, Collections.emptySet());
    }

    public String getName(short gid) {
        return names.getOrDefault(gid, "?");
    }

    public boolean exists(short gid) {
        return members.containsKey(gid);
    }

    /** JSON array con todos los grupos: [{id,name,members},...] */
    public String toJson() {
        StringBuilder sb = new StringBuilder("[");
        members.forEach((gid, m) -> {
            sb.append("{\"id\":").append(Short.toUnsignedInt(gid))
              .append(",\"name\":\"").append(names.getOrDefault(gid, "?")).append("\"")
              .append(",\"members\":").append(m.size()).append("},");
        });
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
}
