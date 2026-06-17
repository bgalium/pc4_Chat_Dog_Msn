package uni.cc4p1.client

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import uni.cc4p1.client.connection.Connection
import uni.cc4p1.client.connection.MessageListener
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var tvUserInfo: TextView
    private lateinit var etMessage: EditText
    private lateinit var etRecipient: EditText
    private lateinit var btnSend: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var tvMessages: TextView
    private lateinit var etPort: EditText
    private lateinit var etIp: EditText
    private lateinit var etUsername: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var btnGroups: Button
    private lateinit var btnAttach: Button
    private lateinit var btnQR: Button
    private lateinit var btnMetrics: Button

    private var connection: Connection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var localUserId: Short = 0
    private var currentUsername: String = ""

    // File picker
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val recId = parseRecipientId()
        addMessage("[FILE] Sending to #${if (recId == 0xFFFF.toShort()) "ALL" else recId.toString()}...")
        sendFileViaProtocol(uri, recId)
    }

    // QR scanner
    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data != null) {
            val contents = data.getStringExtra("SCAN_RESULT")
            if (!contents.isNullOrBlank()) {
                connection?.sendQrRedeem(contents)
                addMessage("[QR] Redeeming token...")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupListeners()
        checkPermissions()
    }

    private fun initViews() {
        tvUserInfo = findViewById(R.id.tvUserInfo)
        etMessage = findViewById(R.id.etMessage)
        etRecipient = findViewById(R.id.etRecipient)
        btnSend = findViewById(R.id.btnSend)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        tvMessages = findViewById(R.id.tvMessages)
        etPort = findViewById(R.id.etPort)
        etIp = findViewById(R.id.etIp)
        etUsername = findViewById(R.id.etUsername)
        scrollView = findViewById(R.id.scrollView)
        btnGroups = findViewById(R.id.btnGroups)
        btnAttach = findViewById(R.id.btnAttach)
        btnQR = findViewById(R.id.btnQR)
        btnMetrics = findViewById(R.id.btnMetrics)
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener { connectToServer() }
        btnDisconnect.setOnClickListener { disconnectFromServer() }
        btnSend.setOnClickListener { sendMessage() }
        btnGroups.setOnClickListener { showGroupDialog() }
        btnAttach.setOnClickListener { pickFile() }
        btnQR.setOnClickListener { showQrDialog() }
        btnMetrics.setOnClickListener { requestMetrics() }
    }

    // ─── CONNECTION ──────────────────────────────────────────────────────────

    private fun connectToServer() {
        val ip = etIp.text.toString().trim()
        val portStr = etPort.text.toString().trim()
        val username = etUsername.text.toString().trim()
        if (ip.isEmpty() || portStr.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Fill IP, port and username", Toast.LENGTH_SHORT).show()
            return
        }
        currentUsername = username
        addMessage("Connecting to $ip:$portStr...")
        connection = Connection(ip, portStr.toInt(), username, object : MessageListener {
            override fun onMessageReceived(message: String) { mainHandler.post { addMessage(message) } }
            override fun onConnected(userId: Short) {
                localUserId = userId
                mainHandler.post {
                    tvUserInfo.text = "$currentUsername | ID: #$localUserId"
                    addMessage("Ready to chat.")
                    enableControls(true)
                    btnConnect.isEnabled = false
                    btnDisconnect.isEnabled = true
                }
            }
            override fun onDisconnected() {
                mainHandler.post {
                    addMessage("Disconnected")
                    tvUserInfo.text = "Disconnected"
                    enableControls(false)
                    btnConnect.isEnabled = true
                    btnDisconnect.isEnabled = false
                }
            }
            override fun onError(error: String) {
                mainHandler.post {
                    addMessage("Error: $error")
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                }
            }
            override fun onQrTokenReceived(token: String) {
                mainHandler.post { showQrCodeDialog(token) }
            }
        })
        connection?.setDownloadDir(filesDir.absolutePath + "/downloads")
        connection?.setSaveFileCallback { filename, data -> saveFileToDownloads(filename, data) }
        connection?.connect()
    }

    private fun disconnectFromServer() {
        addMessage("Disconnecting...")
        connection?.disconnect()
        connection = null
        localUserId = 0
        enableControls(false)
        btnConnect.isEnabled = true
        btnDisconnect.isEnabled = false
    }

    // ─── TEXT MESSAGES ───────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) { Toast.makeText(this, "Write a message", Toast.LENGTH_SHORT).show(); return }
        if (connection == null || !connection!!.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show(); return
        }
        val receiver = parseRecipientId()
        connection?.sendText(text, receiver)
        val label = if (receiver == 0xFFFF.toShort()) "Broadcast" else "To #$receiver"
        addMessage("Me ($label): $text")
        etMessage.text.clear()
    }

    private fun parseRecipientId(): Short {
        val s = etRecipient.text.toString().trim()
        if (s.isEmpty() || s == "0") return 0xFFFF.toShort()
        return s.toShortOrNull() ?: 0xFFFF.toShort()
    }

    // ─── FILE TRANSFER ───────────────────────────────────────────────────────

    private fun pickFile() {
        if (connection == null || !connection!!.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show(); return
        }
        filePickerLauncher.launch("*/*")
    }

    private fun sendFileViaProtocol(uri: android.net.Uri, receiverId: Short) {
        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@Thread
                val fileName = getFileName(uri) ?: "file"
                val baos = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var len: Int
                while (inputStream.read(buf).also { len = it } != -1) {
                    baos.write(buf, 0, len)
                }
                inputStream.close()
                val data = baos.toByteArray()

                val md = MessageDigest.getInstance("SHA-256")
                val sha256 = md.digest(data).joinToString("") { "%02x".format(it) }

                connection?.sendFileStart(fileName, data.size.toLong(), receiverId)
                Thread.sleep(50)
                var offset = 0
                var chunkNum = 0
                while (offset < data.size) {
                    val len = minOf(4096, data.size - offset)
                    val chunk = data.copyOfRange(offset, offset + len)
                    connection?.sendFileChunk(chunk, receiverId)
                    offset += len
                    chunkNum++
                    if (chunkNum % 10 == 0) Thread.sleep(10)
                }
                connection?.sendFileEnd(sha256, receiverId)
                mainHandler.post { addMessage("[FILE] Sent: $fileName ($chunkNum chunks, SHA-256: $sha256)") }
            } catch (e: Exception) {
                mainHandler.post { addMessage("[FILE] Error: ${e.message}") }
            }
        }.start()
    }

    private fun getFileName(uri: android.net.Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    // ─── GROUPS ──────────────────────────────────────────────────────────────

    private fun showGroupDialog() {
        val items = arrayOf("Create Group", "Join Group", "Send to Group", "List Groups")
        AlertDialog.Builder(this)
            .setTitle("Groups")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showGroupCreateDialog()
                    1 -> showGroupJoinDialog()
                    2 -> showGroupMessageDialog()
                    3 -> connection?.sendGroupList()
                }
            }
            .show()
    }

    private fun showGroupCreateDialog() {
        val input = EditText(this)
        input.hint = "Group name"
        AlertDialog.Builder(this)
            .setTitle("Create Group")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) connection?.sendGroupCreate(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGroupJoinDialog() {
        val input = EditText(this)
        input.hint = "Group ID"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("Join Group")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val gid = input.text.toString().trim().toShortOrNull()
                if (gid != null) connection?.sendGroupJoin(gid)
                else Toast.makeText(this, "Invalid group ID", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGroupMessageDialog() {
        val gidInput = EditText(this).apply {
            hint = "Group ID"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val msgInput = EditText(this).apply { hint = "Message" }
        val ll = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(gidInput); addView(msgInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Send to Group")
            .setView(ll)
            .setPositiveButton("Send") { _, _ ->
                val gid = gidInput.text.toString().trim().toShortOrNull()
                val text = msgInput.text.toString().trim()
                if (gid != null && text.isNotEmpty()) connection?.sendGroupMessage(gid, text)
                else Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── QR ──────────────────────────────────────────────────────────────────

    private fun showQrDialog() {
        val items = arrayOf("Generate QR (share this chat)", "Scan QR (clone another chat)")
        AlertDialog.Builder(this)
            .setTitle("QR Clone")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> connection?.sendQrRequest()
                    1 -> scanQrCode()
                }
            }
            .show()
    }

    private fun scanQrCode() {
        try {
            val intent = Intent(this, com.journeyapps.barcodescanner.CaptureActivity::class.java)
            intent.putExtra("SCAN_FORMATS", "QR_CODE")
            qrScannerLauncher.launch(intent)
        } catch (e: Exception) {
            addMessage("[QR] Camera error: ${e.message}. Enter token manually:")
            showManualTokenDialog()
        }
    }

    private fun showManualTokenDialog() {
        val input = EditText(this)
        input.hint = "Paste QR token"
        AlertDialog.Builder(this)
            .setTitle("Enter Token")
            .setView(input)
            .setPositiveButton("Redeem") { _, _ ->
                val token = input.text.toString().trim()
                if (token.isNotEmpty()) connection?.sendQrRedeem(token)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQrCodeDialog(token: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(token, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            val imgView = ImageView(this)
            imgView.setImageBitmap(bitmap)
            AlertDialog.Builder(this)
                .setTitle("QR Code — Share to clone")
                .setView(imgView)
                .setPositiveButton("Close", null)
                .show()
        } catch (e: Exception) {
            addMessage("[QR] Error generating QR: ${e.message}")
        }
    }

    // ─── METRICS ─────────────────────────────────────────────────────────────

    private fun requestMetrics() {
        connection?.sendMetricsRequest()
        addMessage("[METRICS] Requesting...")
    }

    // ─── UI HELPERS ──────────────────────────────────────────────────────────

    private fun addMessage(msg: String) {
        tvMessages.append("$msg\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun enableControls(enabled: Boolean) {
        btnSend.isEnabled = enabled
        etMessage.isEnabled = enabled
        etRecipient.isEnabled = enabled
        btnGroups.isEnabled = enabled
        btnAttach.isEnabled = enabled
        btnQR.isEnabled = enabled
        btnMetrics.isEnabled = enabled
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.INTERNET, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    // ─── FILE DOWNLOAD (public Downloads) ──────────────────────────────────

    private fun saveFileToDownloads(filename: String, data: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, getMimeType(filename))
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    mainHandler.post { addMessage("[FILE] Saved to Downloads/$filename (via MediaStore)") }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val dest = File(downloadsDir, filename)
                FileOutputStream(dest).use { it.write(data) }
                mainHandler.post { addMessage("[FILE] Saved to Downloads/$filename") }
            }
        } catch (e: Exception) {
            mainHandler.post { addMessage("[FILE] Error saving to Downloads: ${e.message}") }
        }
    }

    private fun getMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connection?.disconnect()
    }
}
