package uni.cc4p1.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Un hilo por cliente (Thread-per-Connection).
 * synchronized en send(): evita que dos hilos escriban al mismo socket al mismo tiempo.
 */
public class ClientHandler extends Thread {

    // Sub-comandos para GROUP (type 6)
    static final byte GRP_CREATE  = 0x01;
    static final byte GRP_JOIN    = 0x02;
    static final byte GRP_MESSAGE = 0x03;
    static final byte GRP_LIST    = 0x04;

    // Sub-comandos para QR (type 7)
    static final byte QR_REQUEST = 0x01;
    static final byte QR_REDEEM  = 0x02;

    private final Socket           socket;
    private final DataInputStream  in;
    private final DataOutputStream out;
    private final UserRegistry     registry = UserRegistry.getInstance();
    private final GroupRegistry    groups   = GroupRegistry.getInstance();
    private final MessageHistory   history  = MessageHistory.getInstance();
    private final MetricsCollector metrics  = MetricsCollector.getInstance();
    private final QrTokenStore     qrStore  = QrTokenStore.getInstance();

    private short  userId   = -1;
    private String username = "?";

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.in     = new DataInputStream(socket.getInputStream());
        this.out    = new DataOutputStream(socket.getOutputStream());
        setDaemon(true);
        metrics.recordConnection();
    }

    @Override
    public void run() {
        System.out.println("[Handler] Conexión desde: " + socket.getInetAddress().getHostAddress());
        try {
            while (!socket.isClosed()) {
                Message header  = MessageParser.readHeader(in);
                byte[]  payload = header.payloadLen() > 0
                        ? in.readNBytes(header.payloadLen())
                        : new byte[0];
                metrics.recordMessage(payload.length);
                dispatch(header, payload);
            }
        } catch (IOException e) {
            System.out.println("[Handler] Desconectado: " + username);
        } finally {
            if (userId != -1) registry.remove(userId, username);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void dispatch(Message header, byte[] payload) throws IOException {
        switch (header.type()) {
            case AUTH        -> handleAuth(payload);
            case TEXT        -> handleText(header, payload);
            case FILE_START,
                 FILE_CHUNK,
                 FILE_END   -> relay(header, payload);
            case GROUP       -> handleGroup(header, payload);
            case SALES       -> handleSales(header, payload);
            case QR          -> handleQr(payload);
            case METRICS     -> handleMetrics();
            case DH_EXCHANGE -> handleDhExchange(header, payload);
            default -> System.out.println("[Handler] Tipo sin handler: " + header.type());
        }
    }

    // ─── AUTH ────────────────────────────────────────────────────────────────

    private void handleAuth(byte[] payload) throws IOException {
        username = new String(payload, 0, payload.length - 1, StandardCharsets.UTF_8).trim();
        short assignedId = registry.register(username, this);

        if (assignedId == -1) {
            byte[] msg  = ("Usuario '" + username + "' ya está conectado").getBytes(StandardCharsets.UTF_8);
            byte[] resp = buildAuthPayload((byte) 1, (short) 0, msg);
            send(new Message(resp.length, MessageType.AUTH, (short) 0, (short) 0), resp);
            return;
        }
        this.userId = assignedId;
        byte[] msg  = ("Bienvenido " + username).getBytes(StandardCharsets.UTF_8);
        byte[] resp = buildAuthPayload((byte) 0, assignedId, msg);
        send(new Message(resp.length, MessageType.AUTH, (short) 0, assignedId), resp);
        System.out.println("[Handler] Auth OK: " + username + " id=" + userId);
    }

    private byte[] buildAuthPayload(byte status, short id, byte[] msg) {
        byte[] r = new byte[3 + msg.length];
        r[0] = status;
        r[1] = (byte) (id >> 8);
        r[2] = (byte) (id & 0xFF);
        System.arraycopy(msg, 0, r, 3, msg.length);
        return r;
    }

    // ─── TEXT ────────────────────────────────────────────────────────────────

    private void handleText(Message header, byte[] payload) throws IOException {
        // Guardar en historial (payload puede estar cifrado, guardamos tal cual)
        try {
            String text = new String(payload, StandardCharsets.UTF_8);
            history.store(userId, header.receiverId(), text);
        } catch (Exception ignored) {}
        relay(header, payload);
    }

    // ─── RELAY genérico ──────────────────────────────────────────────────────

    private void relay(Message header, byte[] payload) throws IOException {
        short receiverId = header.receiverId();
        ClientHandler receiver = registry.getHandler(receiverId);
        if (receiver == null) {
            sendError((byte) 2, "Usuario #" + Short.toUnsignedInt(receiverId) + " no está conectado");
            return;
        }
        receiver.send(header, payload);
    }

    // ─── GROUP ───────────────────────────────────────────────────────────────

    private void handleGroup(Message header, byte[] payload) throws IOException {
        if (payload.length == 0) return;
        byte cmd = payload[0];

        switch (cmd) {
            case GRP_CREATE -> {
                String name = new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
                short  gid  = groups.create(name, userId);
                byte[] resp = buildGroupResp(GRP_CREATE, (byte) 0, gid, "Grupo creado: " + name);
                send(new Message(resp.length, MessageType.GROUP, (short) 0, userId), resp);
            }
            case GRP_JOIN -> {
                if (payload.length < 3) return;
                short gid = (short) (((payload[1] & 0xFF) << 8) | (payload[2] & 0xFF));
                boolean ok = groups.join(gid, userId);
                byte[] resp = buildGroupResp(GRP_JOIN, ok ? (byte) 0 : (byte) 1, gid,
                        ok ? "Unido a " + groups.getName(gid) : "Grupo no existe");
                send(new Message(resp.length, MessageType.GROUP, (short) 0, userId), resp);
            }
            case GRP_MESSAGE -> {
                if (payload.length < 3) return;
                short  gid  = (short) (((payload[1] & 0xFF) << 8) | (payload[2] & 0xFF));
                byte[] text = new byte[payload.length - 3];
                System.arraycopy(payload, 3, text, 0, text.length);

                // Broadcast: [GRP_MESSAGE][gid(2B)][sender_id(2B)][text]
                byte[] broadcast = new byte[5 + text.length];
                broadcast[0] = GRP_MESSAGE;
                broadcast[1] = payload[1]; broadcast[2] = payload[2]; // gid
                broadcast[3] = (byte) (userId >> 8);
                broadcast[4] = (byte) (userId & 0xFF);
                System.arraycopy(text, 0, broadcast, 5, text.length);

                Set<Short> members = groups.getMembers(gid);
                for (short memberId : members) {
                    if (memberId == userId) continue;
                    ClientHandler m = registry.getHandler(memberId);
                    if (m != null) m.send(new Message(broadcast.length, MessageType.GROUP, userId, memberId), broadcast);
                }
            }
            case GRP_LIST -> {
                byte[] json = groups.toJson().getBytes(StandardCharsets.UTF_8);
                byte[] resp = new byte[1 + json.length];
                resp[0] = GRP_LIST;
                System.arraycopy(json, 0, resp, 1, json.length);
                send(new Message(resp.length, MessageType.GROUP, (short) 0, userId), resp);
            }
        }
    }

    private byte[] buildGroupResp(byte cmd, byte status, short gid, String msg) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] r = new byte[4 + msgBytes.length];
        r[0] = cmd;
        r[1] = status;
        r[2] = (byte) (gid >> 8);
        r[3] = (byte) (gid & 0xFF);
        System.arraycopy(msgBytes, 0, r, 4, msgBytes.length);
        return r;
    }

    // ─── SALES ───────────────────────────────────────────────────────────────

    private void handleSales(Message header, byte[] payload) throws IOException {
        Short salesId = registry.getIdByUsername("ventas");
        if (salesId == null) {
            sendError((byte) 5, "El nodo de ventas no está conectado. Inicia sales-node/main.py primero.");
            return;
        }
        ClientHandler salesHandler = registry.getHandler(salesId);
        if (salesHandler == null) {
            sendError((byte) 5, "Nodo de ventas no disponible.");
            return;
        }
        // Reenvía el mensaje al nodo de ventas con el sender_id original
        salesHandler.send(new Message(payload.length, MessageType.SALES, userId, salesId), payload);
    }

    // ─── QR ──────────────────────────────────────────────────────────────────

    private void handleQr(byte[] payload) throws IOException {
        if (payload.length == 0) return;
        byte cmd = payload[0];

        if (cmd == QR_REQUEST) {
            String token     = qrStore.generate(userId);
            byte[] tokenBytes = token.getBytes(StandardCharsets.US_ASCII);
            byte[] resp       = new byte[1 + tokenBytes.length];
            resp[0] = QR_REQUEST;
            System.arraycopy(tokenBytes, 0, resp, 1, tokenBytes.length);
            send(new Message(resp.length, MessageType.QR, (short) 0, userId), resp);

        } else if (cmd == QR_REDEEM && payload.length == 37) {
            String token    = new String(payload, 1, 36, StandardCharsets.US_ASCII);
            short  ownerId  = qrStore.redeem(token);

            if (ownerId == -1) {
                byte[] err = "Token inválido o expirado".getBytes(StandardCharsets.UTF_8);
                byte[] resp = new byte[2 + err.length];
                resp[0] = QR_REDEEM; resp[1] = 1;
                System.arraycopy(err, 0, resp, 2, err.length);
                send(new Message(resp.length, MessageType.QR, (short) 0, userId), resp);
                return;
            }
            byte[] histJson = history.toJson(ownerId).getBytes(StandardCharsets.UTF_8);
            byte[] resp     = new byte[2 + histJson.length];
            resp[0] = QR_REDEEM; resp[1] = 0;
            System.arraycopy(histJson, 0, resp, 2, histJson.length);
            send(new Message(resp.length, MessageType.QR, (short) 0, userId), resp);
        }
    }

    // ─── METRICS ─────────────────────────────────────────────────────────────

    private void handleMetrics() throws IOException {
        byte[] json = metrics.toJson().getBytes(StandardCharsets.UTF_8);
        send(new Message(json.length, MessageType.METRICS, (short) 0, userId), json);
    }

    // ─── DH KEY EXCHANGE ─────────────────────────────────────────────────────

    private void handleDhExchange(Message header, byte[] payload) throws IOException {
        short receiver = header.receiverId();

        if (receiver == (short) 0xFFFF) {
            // Cliente registra su clave pública DH
            registry.storeDhKey(userId, payload);
            System.out.println("[DH] Clave pública registrada para user=" + userId);
            return;
        }

        // Cliente solicita intercambio con otro usuario (payload vacío = solicitud)
        if (payload.length == 0) {
            byte[] peerKey = registry.getDhKey(receiver);
            byte[] myKey   = registry.getDhKey(userId);

            if (peerKey != null) {
                // Envía la clave del peer al solicitante
                send(new Message(peerKey.length, MessageType.DH_EXCHANGE, receiver, userId), peerKey);
            }
            if (myKey != null) {
                // Envía la clave del solicitante al peer (para que también derive la clave)
                ClientHandler peerHandler = registry.getHandler(receiver);
                if (peerHandler != null) {
                    peerHandler.send(new Message(myKey.length, MessageType.DH_EXCHANGE, userId, receiver), myKey);
                }
            }
        }
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private void sendError(byte code, String msg) throws IOException {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] payload  = new byte[1 + msgBytes.length];
        payload[0] = code;
        System.arraycopy(msgBytes, 0, payload, 1, msgBytes.length);
        send(new Message(payload.length, MessageType.ERROR, (short) 0, userId), payload);
    }

    /** synchronized: un solo hilo escribe al socket a la vez. */
    public synchronized void send(Message header, byte[] payload) throws IOException {
        MessageParser.writeMessage(out, header, payload);
    }

    public short getUserId() { return userId; }
}
