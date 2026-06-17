package uni.cc4p1.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas de rendimiento del servidor.
 * AtomicInteger/AtomicLong: operaciones lock-free, seguras desde múltiples hilos.
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    private final long          startTime        = System.currentTimeMillis();
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger totalMessages    = new AtomicInteger(0);
    private final AtomicLong    totalBytes       = new AtomicLong(0);

    private MetricsCollector() {}

    public static MetricsCollector getInstance() { return INSTANCE; }

    public void recordConnection()            { totalConnections.incrementAndGet(); }
    public void recordMessage(int payloadLen) { totalMessages.incrementAndGet(); totalBytes.addAndGet(payloadLen); }

    public String toJson() {
        long uptime  = (System.currentTimeMillis() - startTime) / 1000;
        int  online  = UserRegistry.getInstance().getOnlineCount();
        return String.format(
            "{\"uptime_s\":%d,\"online_users\":%d,\"total_connections\":%d," +
            "\"total_messages\":%d,\"total_bytes\":%d}",
            uptime, online, totalConnections.get(), totalMessages.get(), totalBytes.get()
        );
    }
}
