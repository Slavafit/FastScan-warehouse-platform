package com.ocr.fastscan.utils

import android.app.Activity
import android.content.Context
import android.print.PrintManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.ocr.fastscan.data.model.BoxInPartial
import com.ocr.fastscan.ui.MyPrintDocumentAdapter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

class PrintHelper(private val activity: Activity) {

    private val context: Context = activity
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // ВЕРТИКАЛЬНЫЕ РАЗМЕРЫ (100мм ширина x 150мм высота)
    // 1 мм = 2.834 pts
    private val LABEL_WIDTH_PTS = 283  // 100 мм
    private val LABEL_HEIGHT_PTS = 425 // 150 мм

    fun printLabel(
        printManager: PrintManager,
        trackingNumber: String,
        clientCode: String,
        client: String,
        weight: String
    ) {
        val printerIp = getPrinterIp()
        if (printerIp.isNotEmpty()) {
            // Прямая печать ZPL (Вертикальная 100x150)
            val zpl = """
                ^XA
                ^CI28
                ^CF0,40
                ^FO80,40^FDONZA ESPAÑA^FS
                ^BY3,3,100
                ^FO80,100^BCN,100,Y,N,N^FD$trackingNumber^FS
                ^FO80,280^GB600,3,3^FS
                ^CF0,50
                ^FO80,320^FDClient: $clientCode^FS
                ^CF0,35
                ^FO80,380^FDName: $client^FS
                ^CF0,80
                ^FO80,480^FDWeight: $weight kg^FS
                ^XZ
            """.trimIndent()
            sendToPrinter(printerIp, zpl)
        } else {
            executeSystemPrint("Label_$trackingNumber", trackingNumber) { canvas ->
                drawLabelContent(canvas, trackingNumber, clientCode, client, weight)
            }
        }
    }

    fun printBoxLabel(
        printManager: PrintManager,
        trackingNumber: String,
        weight: String,
        last4: String,
        partialName: String,
        length: String,
        width: String,
        height: String
    ) {
        val printerIp = getPrinterIp()
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        
        if (printerIp.isNotEmpty()) {
            // Прямая печать ZPL для Zebra
            // QR-код (^BQN,2,10) и Партия (^A0N,120,120) увеличены
            // Разделение веса и размеров увеличено
            val zpl = """
                ^XA
                ^CI28
                ^CF0,50
                ^FO80,40^FDOnza España^FS
                ^BY3,3,120
                ^FO80,110^BCN,120,Y,N,N^FD$trackingNumber^FS
                ^FO80,330^A0N,80,80^FD$last4^FS
                ^FO80,420^GB600,3,3^FS
                ^CF0,30
                ^FO80,440^FDPrinted in Málaga: $dateStr^FS
                ^FO80,480^GB600,3,3^FS
                ^FO80,510^A0N,120,120^FD$partialName^FS
                ^CF0,50
                ^FO80,660^FDWeight: $weight kg^FS
                ^FO80,760^FDSizes: ${length}x${width}x$height cm^FS
                ^FO80,840^BQN,2,10^FDQA$trackingNumber^FS
                ^XZ
            """.trimIndent()
            sendToPrinter(printerIp, zpl)
        } else {
            executeSystemPrint("Box_$trackingNumber", trackingNumber) { canvas ->
                drawLabelBox(canvas, trackingNumber, weight, last4, partialName, length, width, height)
            }
        }
    }

    fun printTestLabel() {
        val printerIp = getPrinterIp()
        if (printerIp.isEmpty()) {
            Toast.makeText(context, "IP принтера не указан", Toast.LENGTH_SHORT).show()
            return
        }

        val testZpl = """
            ^XA
            ^CI28
            ^CF0,60
            ^FO80,50^FDTEST PRINT^FS
            ^FO80,120^FDZebra TPC 9100^FS
            ^BY3,3,100
            ^FO80,200^BCN,100,Y,N,N^FD12345678^FS
            ^CF0,40
            ^FO80,350^FDIP: $printerIp^FS
            ^FO80,400^FDStatus: OK^FS
            ^XZ
        """.trimIndent()
        
        sendToPrinter(printerIp, testZpl)
    }

    private fun getPrinterIp(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("printer_ip", "192.168.1.168") ?: "192.168.1.168"
    }

    private fun sendToPrinter(ip: String, data: String) {
        scope.launch {
            try {
                val socket = Socket(ip, 9100)
                socket.soTimeout = 5000
                val out: OutputStream = socket.getOutputStream()
                out.write(data.toByteArray(Charsets.UTF_8))
                out.flush()
                socket.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Печать отправлена на Zebra ($ip)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка Zebra: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeSystemPrint(jobName: String, track: String, drawBlock: (Canvas) -> Unit) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize("Label_100x150", "Label 100x150mm", 3937, 5905))
            .setResolution(PrintAttributes.Resolution("res1", "300dpi", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, sig: CancellationSignal?, cb: LayoutResultCallback, ex: Bundle?) {
                cb.onLayoutFinished(PrintDocumentInfo.Builder(jobName).setPageCount(1).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, sig: CancellationSignal?, cb: WriteResultCallback) {
                val pdf = PdfDocument()
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(LABEL_WIDTH_PTS, LABEL_HEIGHT_PTS, 1).create())
                drawBlock(page.canvas)
                pdf.finishPage(page)
                try {
                    pdf.writeTo(FileOutputStream(dest.fileDescriptor))
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    cb.onWriteFailed(e.message)
                } finally {
                    pdf.close()
                }
            }
        }, attributes)
    }

    private fun drawLabelBox(canvas: Canvas, track: String, weight: String, last4: String, partial: String, l: String, w: String, h: String) {
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = true }
        val padding = 28f // ~1 см
        var y = 25f
        
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("Onza España", padding, y, paint)
        y += 30f

        val barcodeBmp = BarcodeUtils.generateBarcode(track, (LABEL_WIDTH_PTS - padding * 2).toInt(), 50)
        canvas.drawBitmap(barcodeBmp, padding, y, null)
        y += 65f

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText(track, padding, y, paint)
        y += 40f

        paint.textSize = 45f 
        paint.isFakeBoldText = true
        canvas.drawText(last4, padding, y, paint)
        y += 10f
        
        canvas.drawLine(padding, y, LABEL_WIDTH_PTS - padding, y, paint)
        y += 18f

        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Printed: $dateStr", padding, y, paint)
        y += 10f
        
        canvas.drawLine(padding, y, LABEL_WIDTH_PTS - padding, y, paint)
        y += 40f

        // Код партии крупнее
        paint.textSize = 45f
        paint.isFakeBoldText = true
        canvas.drawText(partial, padding, y, paint)
        y += 55f

        // Вес и размеры разделены больше
        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("Weight: $weight kg", padding, y, paint)
        y += 40f
        canvas.drawText("Sizes: $l x $w x $h cm", padding, y, paint)
        
        // Крупный QR-код в самом низу
        val qrSize = 90
        val qrBmp = BarcodeUtils.generateQRCode(track, qrSize)
        canvas.drawBitmap(qrBmp, padding, LABEL_HEIGHT_PTS - qrSize - 10f, null)
    }

    private fun drawLabelContent(canvas: Canvas, track: String, code: String, client: String, weight: String) {
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = true }
        val padding = 28f
        var y = 45f

        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("ONZA ESPAÑA - ORDER", padding, y, paint)
        y += 40f

        val barcodeBmp = BarcodeUtils.generateBarcode(track, (LABEL_WIDTH_PTS - padding * 2).toInt(), 80)
        canvas.drawBitmap(barcodeBmp, padding, y, null)
        y += 110f

        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("Track: $track", padding, y, paint)
        y += 60f
        
        paint.textSize = 35f
        paint.isFakeBoldText = true
        canvas.drawText(code, padding, y, paint)
        y += 40f
        
        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText(client, padding, y, paint)
        y += 80f

        paint.textSize = 40f
        paint.isFakeBoldText = true
        canvas.drawText("$weight kg", padding, y, paint)
    }

    fun printParty(partialCode: String, partialName: String, warehouseName: String, dateCreated: String, boxes: List<BoxInPartial>) {
        if (boxes.isEmpty()) return
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { color = Color.BLACK; textSize = 14f }

        var y = 50f
        canvas.drawText("REPORT: $partialName ($partialCode)", 50f, y, paint)
        y += 40f
        for (box in boxes) {
            canvas.drawText("${box.tracking_code} | ${box.gross_weight} kg | ${box.length}x${box.width}x${box.height}", 50f, y, paint)
            y += 20f
            if (y > 800) break
        }
        document.finishPage(page)
        val file = File(context.cacheDir, "report.pdf")
        try {
            FileOutputStream(file).use { document.writeTo(it) }
            val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print("Report_$partialCode", MyPrintDocumentAdapter(file), null)
        } catch (e: Exception) {
            Log.e("PrintHelper", "Report error", e)
        } finally {
            document.close()
        }
    }
}
