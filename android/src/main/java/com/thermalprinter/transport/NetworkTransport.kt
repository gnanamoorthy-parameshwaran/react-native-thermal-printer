package com.thermalprinter.transport

import com.thermalprinter.engine.NetworkPrinter
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

/** The printer never accepted a connection: it's off, unreachable, or busy for too long. */
class PrinterUnreachableException(message: String, cause: Throwable?) : IOException(message, cause)

/** The connection broke while sending. Part of the bill may have printed, so it isn't retried. */
class PrintInterruptedException(cause: Throwable) :
  IOException("Connection lost while sending; the bill may be partly printed", cause)

/**
 * Raw TCP (port 9100) transport that opens a connection per job.
 *
 * These printers serve one connection at a time, so holding a socket open would lock out every
 * other device. Connecting only for the duration of a send lets several phones share a printer;
 * a phone that arrives while another is printing is refused or times out and retries.
 */
class NetworkTransport(
  private val sleep: (Long) -> Unit = Thread::sleep,
  private val random: Random = Random.Default,
) {

  /** Sends [data] as one job and returns the number of connection attempts it took. */
  fun send(printer: NetworkPrinter, data: ByteArray): Int {
    val (socket, attempts) = connect(printer)
    socket.use {
      try {
        it.getOutputStream().apply {
          write(data)
          write(STATUS_REQUEST)
          flush()
        }
        awaitDrained(it, printer.drainTimeoutMs)
      } catch (e: IOException) {
        throw PrintInterruptedException(e)
      }
    }
    return attempts
  }

  /**
   * Waits until the printer has finished the job. Many printers discard unprocessed data when the
   * connection closes, which cuts the bill short.
   *
   * ESC v is answered when the printer reaches it in its queue, so the first reply means the whole
   * job has been parsed; while the paper is still moving the reply has [BUSY] set. Printers that
   * don't answer get [timeoutMs] instead.
   */
  private fun awaitDrained(socket: Socket, timeoutMs: Int) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000L
    val input = socket.getInputStream()
    val output = socket.getOutputStream()
    var status = readStatus(socket, input, timeoutMs) ?: return
    while (status and BUSY != 0) {
      val remainingMs = ((deadline - System.nanoTime()) / 1_000_000).toInt()
      if (remainingMs <= 0) return
      sleep(POLL_MS)
      output.write(STATUS_REQUEST)
      output.flush()
      status = readStatus(socket, input, remainingMs) ?: return
    }
  }

  /** Reads one status reply, or null on timeout. */
  private fun readStatus(socket: Socket, input: InputStream, timeoutMs: Int): Int? {
    socket.soTimeout = timeoutMs.coerceAtLeast(1)
    val first = try {
      input.read()
    } catch (e: SocketTimeoutException) {
      return null
    }
    if (first < 0) return null
    // Replies vary in length between printers; unread bytes would also make close() send a reset.
    sleep(REPLY_SETTLE_MS)
    while (input.available() > 0) input.skip(input.available().toLong())
    return first
  }

  /** Whether the printer accepts a connection right now. Doesn't retry. */
  fun isReachable(printer: NetworkPrinter): Boolean =
    try {
      Socket().use { it.connect(InetSocketAddress(printer.host, printer.port), printer.timeoutMs) }
      true
    } catch (e: IOException) {
      false
    }

  private fun connect(printer: NetworkPrinter): Pair<Socket, Int> {
    var attempt = 0
    while (true) {
      attempt++
      val socket = Socket()
      try {
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(printer.host, printer.port), printer.timeoutMs)
        return socket to attempt
      } catch (e: IOException) {
        socket.close()
        if (e is UnknownHostException || attempt > printer.retries) {
          throw PrinterUnreachableException(
            "Printer at ${printer.host}:${printer.port} not reachable after $attempt attempt(s): ${e.message}",
            e,
          )
        }
        sleep(backoff(attempt))
      }
    }
  }

  /** Exponential, capped at 2s. Jitter stops phones that collided from retrying in lockstep. */
  private fun backoff(attempt: Int): Long =
    minOf(2000L, 250L shl (attempt - 1).coerceAtMost(4)) + random.nextLong(0, 200)

  internal companion object {
    /** ESC v: transmit paper sensor status. */
    val STATUS_REQUEST = byteArrayOf(0x1B, 0x76)

    /** Set in the ESC v reply while the printer is still printing, feeding or cutting. */
    const val BUSY = 0x40

    const val POLL_MS = 50L
    const val REPLY_SETTLE_MS = 5L
  }
}
