package dev.jjtech.eapakaclient.server

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

@Serializable
data class IccAuthRequest(val token: String)

@Serializable
data class IccAuthResponse(val status: String, val received_token: String)

@Serializable
data class InfoResponse(val name: String, val version: String, val port: Int)

class APIServer(private val port: Int) {
    private val json = Json { prettyPrint = true }

    fun start() {
        thread {
            val serverSocket = ServerSocket(port)
            println("API server started on port $port")

            while (true) {
                val client = serverSocket.accept()
                handleClient(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        thread {
            client.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream())

                // Read request line
                val requestLine = reader.readLine() ?: return@use
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@use
                val method = parts[0]
                val path = parts[1]

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    val kv = line.split(":", limit = 2)
                    if (kv.size == 2) headers[kv[0].trim().lowercase()] = kv[1].trim()
                }

                // Read body if present
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    String(buf)
                } else ""

                // Dispatch based on method and path
                val responseBody: String
                val statusLine: String

                when {
                    method == "POST" && path == "/icc_auth" -> {
                        responseBody = handleIccAuth(body)
                        statusLine = "HTTP/1.1 200 OK"
                    }

                    method == "GET" && path == "/info" -> {
                        responseBody = handleInfo()
                        statusLine = "HTTP/1.1 200 OK"
                    }

                    else -> {
                        responseBody = json.encodeToString(mapOf("error" to "Not found"))
                        statusLine = "HTTP/1.1 404 Not Found"
                    }
                }

                val response = buildString {
                    append("$statusLine\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ${responseBody.toByteArray().size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n") // end of headers
                    append(responseBody)
                }

                writer.print(response)
                writer.flush()
            }
        }
    }

    private fun handleIccAuth(body: String): String {
        return try {
            val request = json.decodeFromString<IccAuthRequest>(body)
            val response = IccAuthResponse("ok", request.token)
            json.encodeToString(response)
        } catch (e: Exception) {
            json.encodeToString(mapOf("status" to "error", "message" to e.message))
        }
    }

    private fun handleInfo(): String {
        val info = InfoResponse(
            name = "EAPAKAClient",
            version = "1.0.0",
            port = port
        )
        return json.encodeToString(info)
    }
}