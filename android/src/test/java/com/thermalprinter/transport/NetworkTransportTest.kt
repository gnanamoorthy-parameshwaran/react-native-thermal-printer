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

private fun endsWith(bytes: ByteArrayOutputStream, tail: ByteArray): Boolean {
  val b = bytes.toByteArray()
  return b.size >= tail.size && b.copyOfRange(b.size - tail.size, b.size).contentEquals(tail)
}

/**
 * Plays a printer: reads the job up to the first status request, then answers each request with
 * the next of [statuses] (the last one repeats). With no statuses it never answers. Returns the job.
 */
private fun serveJob(client: Socket, statuses: List<Int> = listOf(IDLE)): ByteArray {
  val input = client.getInputStream()
  val tail = NetworkTransport.STATUS_REQUEST
  val job = ByteArrayOutputStream()
  while (!endsWith(job, tail)) {
    val b = input.read()
    if (b < 0) return job.toByteArray()
    job.write(b)
  }
  val received = job.toByteArray().let { it.copyOf(it.size - tail.size) }
  if (statuses.isEmpty()) {
    input.read()
    return received
  }
  var answered = 0
  var request = ByteArrayOutputStream()
  do {
    client.getOutputStream().apply { write(statuses[minOf(answered++, statuses.size - 1)]); flush() }
    request = ByteArrayOutputStream()
    while (!endsWith(request, tail)) {
      val b = input.read()
      if (b < 0) return received
      request.write(b)
    }
  } while (true)
}

private const val IDLE = 0x14
private const val BUSY = 0x54

class NetworkTransportTest {

  private val noSleep = mutableListOf<Long>()
  private val transport = NetworkTransport(sleep = { noSleep.add(it) })

  @Test
  fun `sends the whole job and waits while the printer reports busy`() {
    ServerSocket(0).use { server ->
      var received = ByteArray(0)
      val printer = thread {
        server.accept().use { received = serveJob(it, listOf(BUSY, BUSY, BUSY, IDLE)) }
      }
      val polls = mutableListOf<Long>()
      val data = ByteArray(100_000) { it.toByte() }

      val attempts = NetworkTransport(sleep = { polls.add(it) })
        .send(NetworkPrinter("127.0.0.1", server.localPort), data)

      printer.join(5000)
      assertEquals(1, attempts)
      assertArrayEquals(data, received)
      // Three busy replies, so three polls.
      assertEquals(3, polls.count { it == NetworkTransport.POLL_MS })
    }
  }

  @Test
  fun `returns as soon as an idle printer answers`() {
    ServerSocket(0).use { server ->
      val printer = thread { server.accept().use { serveJob(it) } }
      val started = System.nanoTime()
      transport.send(NetworkPrinter("127.0.0.1", server.localPort), byteArrayOf(1, 2, 3))
      val elapsedMs = (System.nanoTime() - started) / 1_000_000
      printer.join(5000)
      assertTrue("took $elapsedMs ms", elapsedMs < 1000)
    }
  }

  @Test
  fun `gives up waiting on printers that ignore the status request`() {
    ServerSocket(0).use { server ->
      val printer = thread { server.accept().use { serveJob(it, statuses = emptyList()) } }
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
        repeat(2) { server.accept().use { jobs.add(String(serveJob(it))) } }
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
