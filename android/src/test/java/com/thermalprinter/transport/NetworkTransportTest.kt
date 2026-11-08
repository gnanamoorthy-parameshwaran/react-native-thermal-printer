package com.thermalprinter.transport

import com.thermalprinter.engine.NetworkPrinter
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Reads one job up to the status request and, if [answers], replies with a status byte. */
private fun receiveJob(client: Socket, answers: Boolean = true): ByteArray {
  val input = client.getInputStream()
  val job = ByteArrayOutputStream()
  val tail = NetworkTransport.STATUS_REQUEST
  while (true) {
    val b = input.read()
    if (b < 0) break
    job.write(b)
    val bytes = job.toByteArray()
    if (bytes.size >= tail.size && bytes.copyOfRange(bytes.size - tail.size, bytes.size).contentEquals(tail)) {
      if (answers) client.getOutputStream().apply { write(0x00); flush() }
      break
    }
  }
  return job.toByteArray().let { it.copyOf(maxOf(0, it.size - tail.size)) }
}

class NetworkTransportTest {

  private val noSleep = mutableListOf<Long>()
  private val transport = NetworkTransport(sleep = { noSleep.add(it) })

  @Test
  fun `sends the whole job and stays connected until the printer has processed it`() {
    ServerSocket(0).use { server ->
      var received = ByteArray(0)
      var closedAfterReply = false
      val printer = thread {
        server.accept().use { client ->
          // A slow printer: answers the status request well after the data arrived.
          received = receiveJob(client, answers = false)
          Thread.sleep(300)
          client.getOutputStream().apply { write(0x00); flush() }
          closedAfterReply = client.getInputStream().read() == -1
        }
      }
      val data = ByteArray(100_000) { it.toByte() }

      val started = System.nanoTime()
      val attempts = transport.send(NetworkPrinter("127.0.0.1", server.localPort), data)
      val elapsedMs = (System.nanoTime() - started) / 1_000_000

      printer.join(5000)
      assertEquals(1, attempts)
      assertArrayEquals(data, received)
      assertTrue("disconnected before the printer finished ($elapsedMs ms)", elapsedMs >= 300)
      assertTrue(closedAfterReply)
    }
  }

  @Test
  fun `gives up waiting on printers that ignore the status request`() {
    ServerSocket(0).use { server ->
      val printer = thread { server.accept().use { receiveJob(it, answers = false); it.getInputStream().read() } }
      val started = System.nanoTime()
      transport.send(NetworkPrinter("127.0.0.1", server.localPort, drainTimeoutMs = 400), byteArrayOf(1, 2, 3))
      val elapsedMs = (System.nanoTime() - started) / 1_000_000
      printer.join(5000)
      assertTrue("waited $elapsedMs ms", elapsedMs in 400..2000)
    }
  }

  @Test
  fun `two devices can print one after the other`() {
    ServerSocket(0).use { server ->
      val jobs = mutableListOf<String>()
      val printer = thread {
        repeat(2) { server.accept().use { jobs.add(String(receiveJob(it))) } }
      }
      val target = NetworkPrinter("127.0.0.1", server.localPort)

      transport.send(target, "bill-1".toByteArray())
      NetworkTransport().send(target, "bill-2".toByteArray())

      printer.join(5000)
      assertEquals(listOf("bill-1", "bill-2"), jobs)
    }
  }

  @Test
  fun `retries a refused connection then gives up`() {
    val port = ServerSocket(0).use { it.localPort }
    try {
      transport.send(NetworkPrinter("127.0.0.1", port, timeoutMs = 500, retries = 3), byteArrayOf(1))
      fail("expected PrinterUnreachableException")
    } catch (e: PrinterUnreachableException) {
      assertTrue(e.message!!.contains("4 attempt"))
    }
    assertEquals(3, noSleep.size)
    assertTrue(noSleep[0] in 250L..450L)
    assertTrue(noSleep[2] in 1000L..1200L)
  }

  @Test
  fun `reachability reflects whether anything is listening`() {
    ServerSocket(0).use { server ->
      assertTrue(transport.isReachable(NetworkPrinter("127.0.0.1", server.localPort)))
    }
    val closed = ServerSocket(0).use { it.localPort }
    assertFalse(transport.isReachable(NetworkPrinter("127.0.0.1", closed, timeoutMs = 500)))
  }
}
