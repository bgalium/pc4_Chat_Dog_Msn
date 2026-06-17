package uni.cc4p1.client.connection

interface MessageListener {
    fun onMessageReceived(message: String)
    fun onConnected(userId: Short)
    fun onDisconnected()
    fun onError(error: String)
    fun onQrTokenReceived(token: String)
}
