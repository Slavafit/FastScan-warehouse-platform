package com.ocr.fastscan.utils

import android.content.Context
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
import android.print.PrintManager
import java.io.FileOutputStream

class PrintHelper(private val context: Context) {

    fun printLabel(trackingNumber: String, clientCode: String, weight: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Onza_Label_$trackingNumber"

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                // Описываем документ (1 страница, размер 100x150мм)
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = PdfDocument()
                // 100мм x 150мм переводим в пункты (1/72 дюйма)
                // 100 мм ~ 283 pts, 150 мм ~ 425 pts
                val pageInfo = PdfDocument.PageInfo.Builder(283, 425, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                drawLabelContent(canvas, trackingNumber, clientCode, weight)

                pdfDocument.finishPage(page)

                try {
                    pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    pdfDocument.close()
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }
    fun printBoxLabel(trackingNumber: String, weight: String, last4: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "$trackingNumber"

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                // Описываем документ (1 страница, размер 100x150мм)
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = PdfDocument()
                // 100мм x 150мм переводим в пункты (1/72 дюйма)
                // 100 мм ~ 283 pts, 150 мм ~ 425 pts
                val pageInfo = PdfDocument.PageInfo.Builder(283, 425, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                drawLabelBox(canvas, trackingNumber, weight, last4)

                pdfDocument.finishPage(page)

                try {
                    pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    pdfDocument.close()
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    private fun drawLabelContent(canvas: Canvas, track: String, client: String, weight: String) {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isAntiAlias = true
        }

        // 1. Заголовок
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("ONZA", 20f, 40f, paint)

        // 2. Штрих-код (генерируем Bitmap и рисуем)
        val barcodeBmp = BarcodeUtils.generateBarcode(track, 240, 80)
        canvas.drawBitmap(barcodeBmp, 20f, 60f, paint)

        // 3. Текст под штрих-кодом
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText(track, 70f, 160f, paint)

        // 4. Данные клиента
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Client: $client", 20f, 220f, paint)

        // 5. Вес
        paint.textSize = 30f
        canvas.drawText("Weight: $weight kg", 20f, 270f, paint)

        // Линия разделения
        canvas.drawLine(20f, 180f, 260f, 180f, paint)
    }

    private fun drawLabelBox(canvas: Canvas, trackingCode: String, weight: String, last4: String) {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            isAntiAlias = true
        }

        // Штрих-код (генерируем Bitmap и рисуем)
        val barcodeBmp = BarcodeUtils.generateBarcode(trackingCode, 240, 80)
        canvas.drawBitmap(barcodeBmp, 20f, 60f, paint)

        // Текст под штрих-кодом
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText(trackingCode, 70f, 160f, paint)

        // Линия разделения
        canvas.drawLine(20f, 180f, 320f, 181f, paint)

        // 4 цифры штрих-кода
        paint.textSize = 50f
        paint.isFakeBoldText = false
        canvas.drawText(last4, 85f, 240f, paint)

        // Вес
        paint.textSize = 30f
        canvas.drawText("Weight: $weight kg", 20f, 300f, paint)

    }
}