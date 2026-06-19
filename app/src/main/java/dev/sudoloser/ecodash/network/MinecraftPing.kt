package dev.sudoloser.ecodash.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

data class MinecraftStatus(
    val isOnline: Boolean,
    val motd: String = "",
    val onlinePlayers: Int = 0,
    val maxPlayers: Int = 0,
    val version: String = "",
    val error: String? = null
)

object MinecraftPing {

    suspend fun pingJava(ip: String, port: Int): MinecraftStatus = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 5000)
                val out = socket.getOutputStream()
                val ins = socket.getInputStream()

                // Handshake packet
                val handshakeBytes = ByteArrayOutputStream()
                writeVarInt(handshakeBytes, 0x00) // Packet ID
                writeVarInt(handshakeBytes, 47) // Protocol version (47 is 1.8+, widely accepted)
                writeString(handshakeBytes, ip)
                handshakeBytes.write((port ushr 8) and 0xFF)
                handshakeBytes.write(port and 0xFF)
                writeVarInt(handshakeBytes, 1) // Next state: Status

                // Write Handshake prefixed by length
                writePacket(out, handshakeBytes.toByteArray())

                // Status Request packet
                val requestBytes = ByteArrayOutputStream()
                writeVarInt(requestBytes, 0x00) // Packet ID
                writePacket(out, requestBytes.toByteArray())

                // Read response
                val length = readVarInt(ins)
                val packetId = readVarInt(ins)
                if (packetId != 0x00) {
                    throw RuntimeException("Invalid status response packet ID: $packetId")
                }

                val jsonStr = readString(ins)
                val root = JSONObject(jsonStr)

                val motdText = root.optJSONObject("description")?.optString("text")
                    ?: root.optString("description")

                val playersObj = root.optJSONObject("players")
                val online = playersObj?.optInt("online", 0) ?: 0
                val max = playersObj?.optInt("max", 0) ?: 0
                val versionText = root.optJSONObject("version")?.optString("name") ?: "Java"

                MinecraftStatus(
                    isOnline = true,
                    motd = motdText,
                    onlinePlayers = online,
                    maxPlayers = max,
                    version = versionText
                )
            }
        } catch (e: Exception) {
            MinecraftStatus(isOnline = false, error = e.message ?: "Connection timed out")
        }
    }

    suspend fun pingBedrock(ip: String, port: Int): MinecraftStatus = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 5000
                val address = InetAddress.getByName(ip)

                // RakNet Unconnected Ping Payload
                val byteStream = ByteArrayOutputStream()
                byteStream.write(0x01)
                
                // Write timestamp
                val timestamp = System.currentTimeMillis()
                for (i in 7 downTo 0) {
                    byteStream.write(((timestamp ushr (i * 8)) and 0xFF).toInt())
                }

                // MAGIC constant
                val magic = byteArrayOf(
                    0x00.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00.toByte(),
                    0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
                    0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(),
                    0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()
                )
                byteStream.write(magic)

                // Client GUID (8 bytes random)
                val guid = 1234567890L
                for (i in 7 downTo 0) {
                    byteStream.write(((guid ushr (i * 8)) and 0xFF).toInt())
                }

                val sendData = byteStream.toByteArray()
                val sendPacket = DatagramPacket(sendData, sendData.size, address, port)
                socket.send(sendPacket)

                // Receive buffer
                val receiveData = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                socket.receive(receivePacket)

                // Check packet type: 0x1C is Unconnected Pong
                if (receiveData[0] != 0x1C.toByte()) {
                    throw RuntimeException("Invalid UDP pong packet header: ${receiveData[0]}")
                }

                // Offset by 33 bytes as per RakNet specs
                val stringLength = ((receiveData[33].toInt() and 0xFF) shl 8) or (receiveData[34].toInt() and 0xFF)
                val payload = String(receiveData, 35, stringLength, StandardCharsets.UTF_8)
                val parts = payload.split(";")

                val motd = parts.getOrNull(1) ?: "Bedrock Server"
                val version = parts.getOrNull(3) ?: "Bedrock"
                val onlinePlayers = parts.getOrNull(4)?.toIntOrNull() ?: 0
                val maxPlayers = parts.getOrNull(5)?.toIntOrNull() ?: 0

                MinecraftStatus(
                    isOnline = true,
                    motd = motd,
                    onlinePlayers = onlinePlayers,
                    maxPlayers = maxPlayers,
                    version = version
                )
            }
        } catch (e: Exception) {
            MinecraftStatus(isOnline = false, error = e.message ?: "Connection timed out")
        }
    }

    private fun writePacket(out: java.io.OutputStream, packet: ByteArray) {
        writeVarInt(out, packet.size)
        out.write(packet)
    }

    private fun writeString(out: ByteArrayOutputStream, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readString(ins: java.io.InputStream): String {
        val length = readVarInt(ins)
        val bytes = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = ins.read(bytes, totalRead, length - totalRead)
            if (read == -1) throw java.io.EOFException("Unexpected EOF while reading string bytes")
            totalRead += read
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readVarInt(ins: java.io.InputStream): Int {
        var numRead = 0
        var result = 0
        var read: Int
        do {
            read = ins.read()
            if (read == -1) throw java.io.EOFException("EOF inside VarInt")
            val value = read and 0x7F
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) throw RuntimeException("VarInt too long")
        } while (read and 0x80 != 0)
        return result
    }

    private fun writeVarInt(out: java.io.OutputStream, value: Int) {
        var v = value
        do {
            var temp = v and 0x7F
            v = v ushr 7
            if (v != 0) {
                temp = temp or 0x80
            }
            out.write(temp)
        } while (v != 0)
    }
}
