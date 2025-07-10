package dev.stroe.netlens.server

import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class MjpegHttpServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val clients = ConcurrentHashMap<String, OutputStream>()
    private var currentFrame: ByteArray? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MjpegHttpServer"
    }

    fun start() {
        if (isRunning) return

        isRunning = true
        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "MJPEG Server started on port $port")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            Log.d(TAG, "New client connected: ${socket.remoteSocketAddress}")
                            handleClient(socket)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client connection", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error starting server", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        clients.clear()
        try {
            serverSocket?.close()
            Log.d(TAG, "Server stopped")
        } catch (e: IOException) {
            Log.e(TAG, "Error stopping server", e)
        }
        serverScope.cancel()
    }

    fun updateFrame(frameData: ByteArray) {
        currentFrame = frameData
        Log.d(TAG, "Frame updated: ${frameData.size} bytes, ${clients.size} clients")
        broadcastFrame(frameData)
    }

    private fun handleClient(socket: Socket) {
        serverScope.launch {
            try {
                val output = socket.getOutputStream()
                val clientId = socket.remoteSocketAddress.toString()

                // Read the HTTP request first
                val input = socket.getInputStream()
                val buffer = ByteArray(1024)
                input.read(buffer)

                // Send proper HTTP headers for MJPEG stream
                val headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: multipart/x-mixed-replace; boundary=--boundarydonotcross\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: keep-alive\r\n" +
                        "\r\n"

                output.write(headers.toByteArray())
                output.flush()

                clients[clientId] = output
                Log.d(TAG, "Client $clientId added, total clients: ${clients.size}")

                // Send current frame immediately if available
                currentFrame?.let { frame ->
                    sendFrame(output, frame)
                }

                // Keep connection alive
                while (isRunning && !socket.isClosed) {
                    delay(33) // ~30 FPS
                }

            } catch (e: IOException) {
                Log.e(TAG, "Error handling client", e)
            } finally {
                clients.remove(socket.remoteSocketAddress.toString())
                try {
                    socket.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing client socket", e)
                }
            }
        }
    }

    private fun broadcastFrame(frameData: ByteArray) {
        val clientsToRemove = mutableListOf<String>()

        clients.forEach { (clientId, output) ->
            try {
                sendFrame(output, frameData)
            } catch (e: IOException) {
                Log.w(TAG, "Client $clientId disconnected")
                clientsToRemove.add(clientId)
            }
        }

        clientsToRemove.forEach { clientId ->
            clients.remove(clientId)
        }
    }

    private fun sendFrame(output: OutputStream, frameData: ByteArray) {
        val frameHeader = "\r\n--boundarydonotcross\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${frameData.size}\r\n" +
                "\r\n"

        output.write(frameHeader.toByteArray())
        output.write(frameData)
        output.flush()
    }
}