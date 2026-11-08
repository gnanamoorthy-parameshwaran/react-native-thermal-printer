package com.thermalprinter

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.thermalprinter.engine.Block
import com.thermalprinter.engine.EscPosEncoder
import com.thermalprinter.engine.JobParser
import com.thermalprinter.engine.MonoImage
import com.thermalprinter.engine.Preview
import com.thermalprinter.engine.PrintJob
import com.thermalprinter.engine.ReceiptRenderer
import com.thermalprinter.raster.AndroidRasterizer
import com.thermalprinter.transport.NetworkTransport
import com.thermalprinter.transport.PrintInterruptedException
import com.thermalprinter.transport.PrinterUnreachableException
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ThermalPrinterModule(reactContext: ReactApplicationContext) :
  NativeThermalPrinterSpec(reactContext) {

  // Jobs from this device print one at a time so two bills never interleave. Previews get their
  // own thread so they don't wait behind a print that is retrying against a busy printer.
  private val printExecutor = Executors.newSingleThreadExecutor()
  private val renderExecutor = Executors.newSingleThreadExecutor()
  private val transport = NetworkTransport()
  private val typefaces = ConcurrentHashMap<String, Typeface>()

  override fun print(printerJson: String, jobJson: String, promise: Promise) =
    printExecutor.run(promise) {
      val printer = JobParser.printer(printerJson)
      val job = JobParser.job(jobJson)
      val blocks = render(job)
      val bytes = EscPosEncoder(job.profile).encode(blocks)
      val attempts = transport.send(printer, bytes)
      Arguments.createMap().apply {
        putInt("bytesSent", bytes.size)
        putInt("attempts", attempts)
        putInt("imageBlocks", blocks.count { it is Block.Image })
      }
    }

  override fun preview(jobJson: String, promise: Promise) =
    renderExecutor.run(promise) {
      val job = JobParser.job(jobJson)
      val image = Preview.compose(render(job), job.profile, rasterizer(job))
      Arguments.createMap().apply {
        putString("base64", toPng(image))
        putInt("width", image.width)
        putInt("height", image.height)
      }
    }

  override fun encode(jobJson: String, promise: Promise) =
    renderExecutor.run(promise) {
      val job = JobParser.job(jobJson)
      Base64.encodeToString(EscPosEncoder(job.profile).encode(render(job)), Base64.NO_WRAP)
    }

  override fun checkConnection(printerJson: String, promise: Promise) =
    renderExecutor.run(promise) { transport.isReachable(JobParser.printer(printerJson)) }

  override fun invalidate() {
    printExecutor.shutdownNow()
    renderExecutor.shutdownNow()
    super.invalidate()
  }

  private fun render(job: PrintJob): List<Block> =
    ReceiptRenderer(job.profile, job.mode, rasterizer(job)).render(job.elements)

  private fun rasterizer(job: PrintJob): AndroidRasterizer {
    val typeface = job.image.fontAsset?.let { path ->
      typefaces.getOrPut(path) {
        try {
          Typeface.createFromAsset(reactApplicationContext.assets, path)
        } catch (e: RuntimeException) {
          throw IllegalArgumentException("font '$path' not found in Android assets", e)
        }
      }
    } ?: Typeface.DEFAULT
    return AndroidRasterizer(typeface, job.image.fontSize)
  }

  private fun toPng(image: MonoImage): String {
    val pixels = IntArray(image.width * image.height) { i ->
      if (image[i % image.width, i / image.width]) Color.BLACK else Color.WHITE
    }
    val bitmap = Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    try {
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } finally {
      bitmap.recycle()
    }
  }

  private fun ExecutorService.run(promise: Promise, block: () -> Any?) {
    execute {
      try {
        promise.resolve(block())
      } catch (e: IllegalArgumentException) {
        promise.reject("INVALID_RECEIPT", e.message, e)
      } catch (e: PrinterUnreachableException) {
        promise.reject("PRINTER_UNREACHABLE", e.message, e)
      } catch (e: PrintInterruptedException) {
        promise.reject("PRINT_INTERRUPTED", e.message, e)
      } catch (e: Throwable) {
        promise.reject("PRINT_ERROR", e.message, e)
      }
    }
  }

  companion object {
    const val NAME = NativeThermalPrinterSpec.NAME
  }
}
