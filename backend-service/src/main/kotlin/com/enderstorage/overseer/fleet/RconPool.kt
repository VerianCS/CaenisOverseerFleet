package com.enderstorage.overseer.fleet

import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

// A single in-flight command per node; all socket waits are on bounded gateway workers.
@Component
class RconPool {
    private val sessions = ConcurrentHashMap<String, Connection>()
    fun execute(id: String, host: String, port: Int, password: String, command: String, output: (String) -> Unit): String {
        val connection = sessions.computeIfAbsent(id) { Connection(host,port,password) }
        return try { connection.execute(command, output) } catch (error: Exception) {
            if (sessions.remove(id,connection)) connection.close()
            throw error // Never retry: a timed-out command may already have executed.
        }
    }
    fun disconnect(id: String) { sessions.remove(id)?.close() }
    @Scheduled(fixedDelay=60000)
    fun evict() { sessions.entries.removeIf { if (it.value.idle()) { it.value.close(); true } else false } }
    @PreDestroy fun close() { sessions.values.forEach { it.close() }; sessions.clear() }

    private class Connection(private val host: String, private val port: Int, private val password: String) {
        private var channel: SocketChannel? = null
        private var selector: Selector? = null
        private var sequence = 10
        @Volatile private var lastUsed = System.nanoTime()
        @Volatile private var busy = false
        fun idle() = !busy && System.nanoTime() - lastUsed > Duration.ofMinutes(2).toNanos()

        @Synchronized
        fun execute(command: String, output: (String) -> Unit): String {
            busy = true
            try {
                val deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos()
                if (channel == null) connect(deadline)
                val request = ++sequence
                send(request, 2, command, deadline)
                val fence = ++sequence
                send(fence, 2, "", deadline)
                val collected = ByteArrayOutputStream()
                while (true) {
                    val packet = receive(deadline)
                    if (packet.first == fence) break
                    require(packet.first == request && packet.second == 0) { "Unexpected RCON packet" }
                    require(collected.size() + packet.third.size <= 262144) { "RCON output limit exceeded" }
                    collected.write(packet.third)
                    if (packet.third.isNotEmpty()) output(String(packet.third, Charsets.UTF_8))
                }
                return collected.toString(Charsets.UTF_8)
            } finally { lastUsed = System.nanoTime(); busy = false }
        }
        private fun connect(deadline: Long) {
            val socket = SocketChannel.open()
            channel = socket
            selector = Selector.open()
            socket.configureBlocking(false)
            socket.register(selector, SelectionKey.OP_CONNECT)
            socket.connect(InetSocketAddress(host,port))
            while (!socket.finishConnect()) await(SelectionKey.OP_CONNECT,deadline)
            val id = ++sequence
            send(id,3,password,deadline)
            repeat(8) {
                val packet = receive(deadline)
                require(packet.first != -1) { "RCON authentication rejected" }
                if (packet.second == 2 && packet.first == id) return
            }
            error("RCON authentication response missing")
        }
        private fun send(id: Int, type: Int, text: String, deadline: Long) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(bytes.size + 14).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(bytes.size + 10).putInt(id).putInt(type).put(bytes).put(0.toByte()).put(0.toByte())
            buffer.flip()
            while (buffer.hasRemaining()) if (channel!!.write(buffer) == 0) await(SelectionKey.OP_WRITE,deadline)
        }
        private fun read(size: Int, deadline: Long): ByteBuffer {
            val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            while (buffer.hasRemaining()) {
                val count = channel!!.read(buffer)
                if (count < 0) error("RCON connection closed")
                if (count == 0) await(SelectionKey.OP_READ,deadline)
            }
            buffer.flip(); return buffer
        }
        private fun receive(deadline: Long): Triple<Int,Int,ByteArray> {
            val length = read(4,deadline).int
            require(length in 10..1048576) { "Invalid RCON frame size" }
            val frame = read(length,deadline)
            val id = frame.int
            val type = frame.int
            val body = ByteArray(length - 10)
            frame.get(body)
            require(frame.get() == 0.toByte() && frame.get() == 0.toByte()) { "Invalid RCON terminator" }
            return Triple(id,type,body)
        }
        private fun await(operation: Int, deadline: Long) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw java.net.SocketTimeoutException("RCON response deadline exceeded")
            channel!!.keyFor(selector).interestOps(operation)
            selector!!.select((remaining / 1000000).coerceIn(1,1000))
            selector!!.selectedKeys().clear()
        }
        @Synchronized fun close() {
            runCatching { channel?.close() }; runCatching { selector?.close() }
            channel = null; selector = null
        }
    }
}
