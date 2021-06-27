package com.tunjid.rcswitchcontrol.common

import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.*

interface SocketConnection {
    val port: Int
    val isConnected: Boolean

    fun dispose()
}

interface WritableSocketConnection : SocketConnection {
    val canWrite: Boolean
    suspend fun write(string: String)
}

interface ReadableSocketConnection : SocketConnection {
    val canRead: Boolean
    suspend fun read(): String?
}

interface ReadableWritableSocketConnection : ReadableSocketConnection, WritableSocketConnection

/**
 * Where the connection was made.
 * Server side connections for example represent client sockets.
 */
enum class Side {
    Client, Server
}

fun readWriteSocketConnection(
    socket: Socket,
    side: Side,
): ReadableWritableSocketConnection = ByteChannelSocketConnection(
    socket = socket,
    side = side
)

private class ByteChannelSocketConnection(
    val socket: Socket,
    val side: Side,
) : ReadableWritableSocketConnection {

    private val reader: ByteReadChannel = socket.openReadChannel()
    private val writer: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

    override val port: Int
        get() = when (side) {
            Side.Client -> socket.localAddress.port
            Side.Server -> socket.remoteAddress.port
        }

    override val isConnected get() = !socket.isClosed

    override val canWrite: Boolean
        get() = !writer.isClosedForWrite

    override val canRead: Boolean
        get() = !reader.isClosedForRead

    override suspend fun write(string: String) =
        writer.writeStringUtf8Line(string)

    override suspend fun read(): String? =
        reader.readUTF8Line()

    override fun dispose() {
        println("Closing $side; read error: ${reader.closedCause}; write error: ${writer.closedCause}")
        socket.dispose()
    }
}

private suspend fun ByteWriteChannel.writeStringUtf8Line(string: String) =
    writeFully("$string\r\n".toByteArray())
