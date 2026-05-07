package com.specificlanguages.mops.daemon

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleUsePingServerTest {
    private val gson = Gson()

    @Test
    fun `successful ping returns project and mps home`() {
        val response = exchange(
            request = PingRequest(type = "ping", protocolVersion = 1, token = "secret"),
        )

        assertEquals("ok", response.status)
        assertEquals(1, response.protocolVersion)
        assertEquals("/project", response.projectPath)
        assertEquals("/mps", response.mpsHome)
    }

    @Test
    fun `token mismatch returns structured error`() {
        val response = exchange(
            request = PingRequest(type = "ping", protocolVersion = 1, token = "wrong"),
        )

        assertEquals("error", response.status)
        assertEquals("TOKEN_MISMATCH", response.errorCode)
    }

    @Test
    fun `protocol mismatch returns structured error`() {
        val response = exchange(
            request = PingRequest(type = "ping", protocolVersion = 999, token = "secret"),
        )

        assertEquals("error", response.status)
        assertEquals("PROTOCOL_MISMATCH", response.errorCode)
    }

    private fun exchange(request: PingRequest): PingResponse {
        val latch = CountDownLatch(1)
        lateinit var ready: ReadyMessage
        val server = SingleUsePingServer(
            projectPath = "/project",
            mpsHome = "/mps",
            expectedToken = "secret",
        )
        val thread = Thread {
            server.serveOnce {
                ready = it
                latch.countDown()
            }
        }
        thread.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "server did not bind a socket")
        val response = Socket(InetAddress.getLoopbackAddress(), ready.port).use { socket ->
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(gson.toJson(request))
                    gson.fromJson(reader.readLine(), PingResponse::class.java)
                }
            }
        }
        thread.join(5_000)
        assertTrue(!thread.isAlive, "server did not exit after one request")
        return response
    }
}
