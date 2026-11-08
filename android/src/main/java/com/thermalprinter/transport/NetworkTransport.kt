package com.thermalprinter.transport

import com.thermalprinter.engine.NetworkPrinter
import java.io.IOException
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
   * Waits until the printer has processed everything sent. Many printers discard unprocessed data
   * when the connection closes, which cuts the bill short. GS r is answered in order, after all
   * earlier data; printers that ignore it are given [timeoutMs] instead.
   */
  private fun awaitDrained(socket: Socket, timeoutMs: Int) {
    socket.soTimeout = timeoutMs
    val input = socket.getInputStream()
    try {
      input.read()
    } catch (e: SocketTimeoutException) {
      return
    }
    // Leftover unread bytes would make close() send a reset instead of a normal close.
    while (input.available() > 0) input.skip(input.available().toLong())
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
    /** GS r 1: transmit paper sensor status. */
    val STATUS_REQUEST = byteArrayOf(0x1D, 0x72, 0x01)
  }
}
