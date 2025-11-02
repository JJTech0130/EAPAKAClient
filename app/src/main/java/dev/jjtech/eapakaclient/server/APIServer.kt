package dev.jjtech.eapakaclient.server

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dev.jjtech.eapakaclient.eapaka.BytesConverter.convertBytesToHexString
import dev.jjtech.eapakaclient.eapaka.EapAkaChallenge
import dev.jjtech.eapakaclient.eapaka.EapAkaChallenge.parseEapAkaChallenge
import dev.jjtech.eapakaclient.eapaka.EapAkaResponse.respondToEapAkaChallenge
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

@Serializable
data class EapAkaRequest(val challenge: String)

@Serializable
data class EapAkaResponse(val status: String, val response: String)

@Serializable
data class InfoResponse(val name: String, val version: String, val port: Int)

class APIServer(private val ctx: Context, private val port: Int) {
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
                    method == "POST" && path == "/eap_aka" -> {
                        responseBody = handleEapAka(body)
                        statusLine = "HTTP/1.1 200 OK"
                    }

                    method == "GET" && path == "/info" -> {
                        responseBody = handleInfo()
                        statusLine = "HTTP/1.1 200 OK"
                    }

                    method == "GET" && path.startsWith("/?") -> {
                        // Parse the parameters
                        val params = parseQueryParams(path)
                        responseBody = handleQuery(params)
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

    private fun handleEapAka(body: String): String {
        return try {
            val request = json.decodeFromString<EapAkaRequest>(body)

            Log.i("EAP-AKA", "Challenge ${request.challenge}")
            val defaultVoiceSubId = SubscriptionManager.getDefaultSmsSubscriptionId()

            Log.i("EAP-AKA", "Default subId $defaultVoiceSubId")
            val challenge = parseEapAkaChallenge(request.challenge);

            val result = respondToEapAkaChallenge(ctx, defaultVoiceSubId, challenge, "nai.epc").response()
            Log.i("EAP-AKA", "Response $result")
            if (result == null) {
                throw RuntimeException("Response is null")
            }

            val response = EapAkaResponse("ok", result)
            json.encodeToString(response)
        } catch (e: Exception) {
            json.encodeToString(EapAkaResponse("error", e.message ?: "Unknown error"))
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

    private fun parseQueryParams(path: String): Map<String, String> {
        val queryStart = path.indexOf('?')
        if (queryStart == -1) return emptyMap()
        val query = path.substring(queryStart + 1)
        return query.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun handleQuery(params: Map<String, String>): String {
        return try {
            when (params["type"]) {
                "imsi" -> {
                    val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                    val telephonyManager: TelephonyManager =
                        ctx.getSystemService<TelephonyManager>(TelephonyManager::class.java)
                            .createForSubscriptionId(subId)
                    //val tm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                    val imsi = telephonyManager.subscriberId
                    json.encodeToString(mapOf("imsi" to imsi))
                }

                "rand-autn" -> {
                    val rand = params["rand"] ?: throw IllegalArgumentException("Missing rand")
                    val autn = params["autn"] ?: throw IllegalArgumentException("Missing autn")

                    val defaultVoiceSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
                    val challenge = EapAkaChallenge(rand, autn);

                    val result = respondToEapAkaChallenge(ctx, defaultVoiceSubId, challenge, "nai.epc")
                    val secContext = result.rawContext
                    val res = convertBytesToHexString(secContext.res)
                    val ck = convertBytesToHexString(secContext.ck)
                    val ik = convertBytesToHexString(secContext.ik)

                    json.encodeToString(mapOf("res" to res, "ck" to ck, "ik" to ik))
                }

                else -> json.encodeToString(mapOf("error" to "Unknown type"))
            }
        } catch (e: Exception) {
            json.encodeToString(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
}