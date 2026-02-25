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
            isAntiAlias = true
        }

        // 1. Штрих-код
        // Рисуем чуть выше (y = 40f), чтобы сэкономить место.
        // Занимает пространство от 40 до 160 по вертикали (40 + 120).
        val barcodeBmp = BarcodeUtils.generateBarcode(trackingCode, 360, 120)
        canvas.drawBitmap(barcodeBmp, 20f, 40f, paint)

        // 2. Полный трек-номер прямо под штрих-кодом
        paint.textSize = 30f // Сделали чуть крупнее (было 16f) для читаемости
        paint.isFakeBoldText = false
        // Рисуем на уровне 185 (ровно под штрих-кодом). Сдвинули X на 40, чтобы текст был центричнее.
        canvas.drawText(trackingCode, 50f, 185f, paint)

        // 3. Линия разделения
        paint.strokeWidth = 2f // Делаем линию чуть толще для термопринтера
        // Растягиваем линию от 20 до 380, чтобы она была ровно по ширине штрих-кода (20 + 360 = 380)
        canvas.drawLine(20f, 210f, 380f, 210f, paint)

        // 4. Последние 4 цифры (Крупно)
        paint.textSize = 70f // Делаем еще крупнее (было 50)
        paint.isFakeBoldText = true // Включаем жирный шрифт для акцента
        // X=110 сдвигает цифры ближе к центру, Y=280 дает отступ от линии
        canvas.drawText(last4, 90f, 280f, paint)

        // 5. Вес
        paint.textSize = 35f // Чуть крупнее для склада
        paint.isFakeBoldText = false // Возвращаем обычный шрифт
        canvas.drawText("Weight: $weight kg", 20f, 340f, paint)
    }
}