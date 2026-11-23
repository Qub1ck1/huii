package com.example.tgwsproxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString
import java.io.Closeable
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class TelegramWsTunnel(
    private val clientSocket: Socket,
    private val activeTunnels: MutableSet<Closeable>,
    private val onCountChanged: () -> Unit
) : Closeable {

    companion object {
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.MILLISECONDS)
            .build()

        fun resolveWsEndpoint(ip: String): String? {
            val octets = ip.split(".").mapNotNull { it.toIntOrNull() }
            if (octets.size != 4) return null
            val (a, b, c, d) = octets

            return when {
                a == 149 && b == 154 && c == 175 && d < 80 -> "wss://kws1.web.telegram.org/apiws"
                a == 149 && b == 154 && c == 167 && d < 80 -> "wss://kws2.web.telegram.org/apiws"
                a == 149 && b == 154 && c == 175 && d >= 80 -> "wss://kws3.web.telegram.org/apiws"
                a == 149 && b == 154 && c == 167 && d >= 80 -> "wss://kws4.web.telegram.org/apiws"
                a == 91 && b == 108 && c == 56 -> "wss://kws5.web.telegram.org/apiws"
                a == 149 && b == 154 -> "wss://kws2.web.telegram.org/apiws"
                a == 91 && b == 108 -> "wss://kws4.web.telegram.org/apiws"
                else -> null
            }
        }
    }

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var fallbackSocket: Socket? = null

    suspend fun run() = withContext(Dispatchers.IO) {
        activeTunnels.add(this@TelegramWsTunnel)
        onCountChanged()
        try {
            clientSocket.soTimeout = 25_000
            clientSocket.tcpNoDelay = true

            val input = DataInputStream(clientSocket.getInputStream())
            val output = clientSocket.getOutputStream()

            if (input.readUnsignedByte() != 0x05) return@withContext
            val methodsCount = input.readUnsignedByte()
            input.readFully(ByteArray(methodsCount))
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val ver = input.readUnsignedByte()
            val cmd = input.readUnsignedByte()
            input.readUnsignedByte()
            val atyp = input.readUnsignedByte()
            if (ver != 0x05 || cmd != 0x01) return@withContext

            val host = when (atyp) {
                0x01 -> {
                    val rawIp = ByteArray(4)
                    input.readFully(rawIp)
                    InetAddress.getByAddress(rawIp).hostAddress ?: return@withContext
                }
                0x03 -> {
                    val len = input.readUnsignedByte()
                    val domain = ByteArray(len)
                    input.readFully(domain)
                    String(domain, Charsets.UTF_8)
                }
                else -> return@withContext
            }
            val port = input.readUnsignedShort()

            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            val wsUrl = resolveWsEndpoint(host)
            if (wsUrl != null) {
                pipeWebSocket(wsUrl, input, output)
            } else {
                pipeDirectTcp(host, port, input, output)
            }
        } catch (_: Exception) {
        } finally {
            close()
        }
    }

    private fun pipeWebSocket(wsUrl: String, input: DataInputStream, output: OutputStream) {
        val request = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", "binary")
            .header("Origin", "https://web.telegram.org")
            .build()

        val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val arr = bytes.toByteArray()
                    output.write(arr)
                    output.flush()
                    ProxyState.addBytesDown(arr.size.toLong())
                } catch (_: Exception) {
                    close()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = close()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = close()
        })
        webSocket = ws

        val buffer = ByteArray(16 * 1024)
        while (!clientSocket.isClosed) {
            val read = input.read(buffer)
            if (read <= 0) break
            if (!ws.send(ByteString.of(buffer, 0, read))) break
            ProxyState.addBytesUp(read.toLong())
        }
    }

    private fun pipeDirectTcp(host: String, port: Int, input: DataInputStream, output: OutputStream) {
        val remote = Socket()
        remote.connect(InetSocketAddress(host, port), 10_000)
        remote.soTimeout = 25_000
        fallbackSocket = remote

        val reader = Thread {
            try {
                val rIn = remote.getInputStream()
                val buf = ByteArray(16 * 1024)
                while (!remote.isClosed && !clientSocket.isClosed) {
                    val n = rIn.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    output.flush()
                    ProxyState.addBytesDown(n.toLong())
                }
            } catch (_: Exception) {
            } finally {
                close()
            }
        }
        reader.start()

        val buf = ByteArray(16 * 1024)
        while (!clientSocket.isClosed && !remote.isClosed) {
            val n = input.read(buf)
            if (n <= 0) break
            remote.getOutputStream().write(buf, 0, n)
            remote.getOutputStream().flush()
            ProxyState.addBytesUp(n.toLong())
        }
    }

    override fun close() {
        if (activeTunnels.remove(this)) {
            onCountChanged()
        }
        try { webSocket?.close(1000, null) } catch (_: Exception) {}
        try { fallbackSocket?.close() } catch (_: Exception) {}
        try { clientSocket.close() } catch (_: Exception) {}
    }
}
