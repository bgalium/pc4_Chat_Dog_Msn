package uni.cc4p1.client.connection

import android.util.Log
import uni.cc4p1.client.model.Message
import uni.cc4p1.client.model.MessageParser
import uni.cc4p1.client.model.MessageType
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

class Connection(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val listener: MessageListener
) {
    companion object {
        private const val TAG = "TCPConnection"
        const val GRP_CREATE: Byte = 0x01
        const val GRP_JOIN: Byte = 0x02
        const val GRP_MESSAGE: Byte = 0x03
        const val GRP_LIST: Byte = 0x04
        const val QR_REQUEST: Byte = 0x01
        const val QR_REDEEM: Byte = 0x02
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    @Volatile private var running = false
    private var readThread: Thread? = null
    private var senderId: Short = 0

    // File download state
    private var downloadStream: ByteArrayOutputStream? = null
    private var downloadFilename: String? = null
    private var downloadFileSize: Long = 0

    fun connect() {
        readThread = Thread {
            try {
                running = true
                val address = InetAddress.getByName(host)
                socket = Socket(address, port)
                input = DataInputStream(socket!!.getInputStream())
                output = DataOutputStream(socket!!.getOutputStream())
                Log.d(TAG, "Connected to $host:$port")
                sendAuth()
                while (running) {
                    try {
                        val header = MessageParser.readHeader(input!!)
                        val payload = if (header.payloadLen > 0) {
                            val buf = ByteArray(header.payloadLen)
                            input!!.readFully(buf)
                            buf
                        } else ByteArray(0)
                        try { dispatch(header, payload) }
                        catch (e: Exception) { Log.e(TAG, "Dispatch error: ${e.message}") }
                    } catch (e: IOException) {
                        if (running) { listener.onError("Connection lost: ${e.message}") }
                        break
                    }
                }
            } catch (e: Exception) {
                listener.onError("Connection failed: ${e.message}")
            } finally {
                running = false
                try { socket?.close() } catch (_: Exception) {}
                socket = null; input = null; output = null
            }
        }
        readThread?.start()
    }

    private fun dispatch(header: Message, payload: ByteArray) {
        when (header.type) {
            MessageType.AUTH -> handleAuth(header, payload)
            MessageType.TEXT -> {
                val text = String(payload, Charsets.UTF_8)
                val who = when (header.senderId) {
                    senderId -> "Me"
                    0.toShort() -> "Server"
                    else -> "#${header.senderId}"
                }
                listener.onMessageReceived("$who: $text")
            }
            MessageType.FILE_START -> handleFileStart(header, payload)
            MessageType.FILE_CHUNK -> handleFileChunk(payload)
            MessageType.FILE_END -> handleFileEnd(payload)
            MessageType.GROUP -> handleGroup(header, payload)
            MessageType.QR -> handleQr(payload)
            MessageType.METRICS -> listener.onMessageReceived("[METRICS] ${String(payload, Charsets.UTF_8)}")
            MessageType.DH_EXCHANGE -> listener.onMessageReceived("[DH] Key exchange with #${header.senderId}")
            MessageType.SALES -> listener.onMessageReceived("[SALES] ${String(payload, Charsets.UTF_8)}")
            MessageType.ERROR -> {
                val err = if (payload.size > 1) String(payload, 1, payload.size - 1, Charsets.UTF_8)
                          else "Unknown"
                listener.onError(err)
            }
        }
    }

    // ─── AUTH ─────────────────────────────────────────────────────────────────

    private fun handleAuth(header: Message, payload: ByteArray) {
        if (payload.size < 3) { listener.onError("Invalid auth response"); return }
        val status = payload[0].toInt() and 0xFF
        val assignedId = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        val msg = if (payload.size > 3) String(payload, 3, payload.size - 3, Charsets.UTF_8) else ""
        if (status == 0) {
            senderId = assignedId.toShort()
            listener.onMessageReceived("Auth OK: $msg (id=$assignedId)")
            listener.onConnected(senderId)
        } else { listener.onError("Auth failed: $msg") }
    }

    private fun sendAuth() {
        val payload = (username + '\u0000').toByteArray(Charsets.UTF_8)
        sendMessage(Message(payload.size, MessageType.AUTH, 0.toShort(), 0xFFFF.toShort()), payload)
    }

    // ─── FILE TRANSFER (receive) ─────────────────────────────────────────────

    private fun handleFileStart(header: Message, payload: ByteArray) {
        try {
            val dis = java.io.DataInputStream(java.io.ByteArrayInputStream(payload))
            val fnLen = dis.readShort().toInt() and 0xFFFF
            val fnBytes = ByteArray(fnLen); dis.readFully(fnBytes)
            downloadFilename = String(fnBytes, Charsets.UTF_8)
            downloadFileSize = dis.readLong()
            downloadStream = ByteArrayOutputStream()
            listener.onMessageReceived("[FILE] Receiving: $downloadFilename (${downloadFileSize}B) from #${header.senderId}")
        } catch (e: Exception) { listener.onError("File start error: ${e.message}") }
    }

    private fun handleFileChunk(payload: ByteArray) {
        downloadStream?.write(payload)
    }

    private fun handleFileEnd(payload: ByteArray) {
        try {
            val shaReceived = String(payload, Charsets.US_ASCII)
            val data = downloadStream?.toByteArray() ?: ByteArray(0)
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val shaActual = md.digest(data).joinToString("") { "%02x".format(it) }
            val match = shaActual == shaReceived
            val filename = downloadFilename ?: "file"
            val saveCallback = saveFileCallback
            if (saveCallback != null) {
                saveCallback(filename, data)
            } else {
                val dir = java.io.File(downloadDir)
                if (!dir.exists()) dir.mkdirs()
                val dest = java.io.File(dir, "recv_$filename")
                java.io.FileOutputStream(dest).use { it.write(data) }
            }
            listener.onMessageReceived("[FILE] ${if (match) "OK" else "CHECKSUM MISMATCH"} -> $filename (SHA-256: $shaActual)")
        } catch (e: Exception) { listener.onError("File save error: ${e.message}") }
        downloadStream = null; downloadFilename = null; downloadFileSize = 0
    }

    private var downloadDir: String = "${System.getProperty("user.dir")}/downloads"
    private var saveFileCallback: ((filename: String, data: ByteArray) -> Unit)? = null

    fun setDownloadDir(path: String) { downloadDir = path }

    fun setSaveFileCallback(callback: (String, ByteArray) -> Unit) { saveFileCallback = callback }

    // ─── FILE TRANSFER (send) ────────────────────────────────────────────────

    fun sendFileStart(filename: String, fileSize: Long, receiverId: Short) {
        val fnBytes = filename.toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(fnBytes.size)
        dos.write(fnBytes)
        dos.writeLong(fileSize)
        dos.flush()
        sendMessage(Message(baos.size(), MessageType.FILE_START, senderId, receiverId), baos.toByteArray())
    }

    fun sendFileChunk(data: ByteArray, receiverId: Short) {
        sendMessage(Message(data.size, MessageType.FILE_CHUNK, senderId, receiverId), data)
    }

    fun sendFileEnd(sha256: String, receiverId: Short) {
        val payload = sha256.toByteArray(Charsets.US_ASCII)
        sendMessage(Message(payload.size, MessageType.FILE_END, senderId, receiverId), payload)
    }

    // ─── TEXT ────────────────────────────────────────────────────────────────

    fun sendText(text: String, receiverId: Short = 0xFFFF.toShort()) {
        val payload = text.toByteArray(Charsets.UTF_8)
        sendMessage(Message(payload.size, MessageType.TEXT, senderId, receiverId), payload)
    }

    // ─── GROUP ───────────────────────────────────────────────────────────────

    private fun handleGroup(header: Message, payload: ByteArray) {
        if (payload.isEmpty()) return
        when (payload[0]) {
            GRP_CREATE, GRP_JOIN -> {
                if (payload.size < 4) return
                val ok = payload[1].toInt() == 0
                val gid = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                val msg = String(payload, 4, payload.size - 4, Charsets.UTF_8)
                listener.onMessageReceived("[GROUP] ${if (ok) msg else "Error: $msg"} (gid=$gid)")
            }
            GRP_MESSAGE -> {
                if (payload.size < 5) return
                val gid = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
                val from = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
                val text = String(payload, 5, payload.size - 5, Charsets.UTF_8)
                listener.onMessageReceived("[Group #$gid] #$from: $text")
            }
            GRP_LIST -> listener.onMessageReceived("[GROUPS] ${String(payload, 1, payload.size - 1, Charsets.UTF_8)}")
        }
    }

    fun sendGroupCreate(name: String) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + nameBytes.size)
        payload[0] = GRP_CREATE; System.arraycopy(nameBytes, 0, payload, 1, nameBytes.size)
        sendMessage(Message(payload.size, MessageType.GROUP, senderId, 0xFFFF.toShort()), payload)
    }

    fun sendGroupJoin(gid: Short) {
        val payload = byteArrayOf(GRP_JOIN, (gid.toInt() shr 8).toByte(), gid.toByte())
        sendMessage(Message(payload.size, MessageType.GROUP, senderId, 0xFFFF.toShort()), payload)
    }

    fun sendGroupMessage(gid: Short, text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(3 + textBytes.size)
        payload[0] = GRP_MESSAGE; payload[1] = (gid.toInt() shr 8).toByte(); payload[2] = gid.toByte()
        System.arraycopy(textBytes, 0, payload, 3, textBytes.size)
        sendMessage(Message(payload.size, MessageType.GROUP, senderId, 0xFFFF.toShort()), payload)
    }

    fun sendGroupList() { sendMessage(Message(1, MessageType.GROUP, senderId, 0xFFFF.toShort()), byteArrayOf(GRP_LIST)) }

    // ─── QR ──────────────────────────────────────────────────────────────────

    private fun handleQr(payload: ByteArray) {
        if (payload.isEmpty()) return
        when (payload[0]) {
            QR_REQUEST -> {
                val token = String(payload, 1, payload.size - 1, Charsets.US_ASCII)
                listener.onQrTokenReceived(token)
            }
            QR_REDEEM -> {
                if (payload.size < 2) return
                val ok = payload[1].toInt() == 0
                val content = String(payload, 2, payload.size - 2, Charsets.UTF_8)
                listener.onMessageReceived(if (ok) "[QR] History cloned!" else "[QR] Error: $content")
            }
        }
    }

    fun sendQrRequest() { sendMessage(Message(1, MessageType.QR, senderId, 0xFFFF.toShort()), byteArrayOf(QR_REQUEST)) }

    fun sendQrRedeem(token: String) {
        val payload = ByteArray(1 + token.length)
        payload[0] = QR_REDEEM
        System.arraycopy(token.toByteArray(Charsets.US_ASCII), 0, payload, 1, token.length)
        sendMessage(Message(payload.size, MessageType.QR, senderId, 0xFFFF.toShort()), payload)
    }

    // ─── METRICS ─────────────────────────────────────────────────────────────

    fun sendMetricsRequest() { sendMessage(Message(0, MessageType.METRICS, senderId, 0xFFFF.toShort()), ByteArray(0)) }

    // ─── LOW-LEVEL SEND ──────────────────────────────────────────────────────

    private fun sendMessage(header: Message, payload: ByteArray) {
        Thread {
            try {
                synchronized(this) {
                    if (output != null) {
                        MessageParser.writeMessage(output!!, header, payload)
                        Log.d(TAG, "Sent: $header")
                    }
                }
            } catch (e: Exception) { listener.onError("Send failed: ${e.message}") }
        }.start()
    }

    fun disconnect() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
        listener.onDisconnected()
    }

    fun isConnected(): Boolean = socket != null && socket!!.isConnected && !socket!!.isClosed
}
