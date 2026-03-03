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
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import com.ocr.fastscan.data.model.BoxInPartial
import com.ocr.fastscan.ui.MyPrintDocumentAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrintHelper(private val context: Context) {

    fun printLabel(trackingNumber: String, clientCode: String, client: String, weight: String) {
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

                drawLabelContent(canvas, trackingNumber, clientCode, client, weight)

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
    fun printBoxLabel(
        trackingNumber: String,
        weight: String,
        last4: String,
        partialName: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Log.e("PrintHelper", "PrintManager недоступен")
            return
        }

        val jobName = "Этикетка $trackingNumber"

        printManager.print(jobName, object : PrintDocumentAdapter() {

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                // Настраиваем атрибуты печати
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A6.asPortrait()) // или CUSTOM размер
                    //.setResolution(PrintAttributes.Resolution(300, "300dpi", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()

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
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(283, 425, 1).create() // 100×150 мм
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                drawLabelBox(canvas, trackingNumber, weight, last4, partialName)

                pdfDocument.finishPage(page)

                try {
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        pdfDocument.writeTo(output)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    pdfDocument.close()
                }
            }
        }, null)
    }

    private fun drawLabelBox(
        canvas: Canvas,
        trackingNumber: String,
        weight: String,
        last4: String,
        partialName: String
    ) {

        val padding = 20f // Отступ от краев этикетки в пикселях
        val usableWidth = canvas.width - (padding * 2) // Доступная ширина для самого кода
        var y = 30f
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }
        val barcodePaint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }

        // Заголовок
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("Onza España", padding + 20f, y, paint)
        y += 40f

        //Штрих-код (генерируем Bitmap и рисуем)
        val barcodeHeight = 100
        // Генерируем код ровно под ширину этикетки минус отступы
        val barcodeBmp = BarcodeUtils.generateBarcode(trackingNumber, usableWidth.toInt(), barcodeHeight)
        // Рисуем строго по центру
        canvas.drawBitmap(barcodeBmp, padding, y, barcodePaint)
        y += (barcodeHeight + 20f)


        // Трек-номер
        paint.textSize = 18f
        paint.isFakeBoldText = false // Обычный шрифт для длинного номера
        canvas.drawText(trackingNumber, padding + 60f, y, paint)
        y += 70f

        // Последние 4 цифры крупно
        paint.textSize = 40f
        paint.isFakeBoldText = true
        canvas.drawText(last4, padding + 90f, y, paint)
        y += 40f


        // Партия
        paint.textSize = 30f
        paint.isFakeBoldText = false
        canvas.drawText("KG $partialName", padding + 40f, y, paint)
        y += 40f

        // Вес
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText("Weight brutto: $weight kg", padding + 40f, y, paint)
        y += 60f

        // Дата печати
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Printed: $dateStr", padding + 40f, y, paint)
    }

    private fun drawLabelContent(canvas: Canvas, track: String, clientCode: String, client: String, weight: String) {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isAntiAlias = true
        }

        // 1. Заголовок
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("ONZA ESPAÑA", 20f, 40f, paint)

        // 2. Штрих-код (генерируем Bitmap и рисуем)
        val barcodeBmp = BarcodeUtils.generateBarcode(track, 240, 80)
        canvas.drawBitmap(barcodeBmp, 20f, 60f, paint)

        // 3. Текст под штрих-кодом
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText(track, 70f, 160f, paint)

        // Линия разделения
        canvas.drawLine(20f, 180f, 260f, 180f, paint)

        // 4. Данные клиента
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Client Code: $clientCode", 20f, 220f, paint)

        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Client: $client", 20f, 270f, paint)

        // 5. Вес
        paint.textSize = 25f
        canvas.drawText("Weight: $weight kg", 20f, 320f, paint)


    }
    private fun drawLabelBox(canvas: Canvas, trackingCode: String, weight: String, last4: String, ) {
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }

        // 1. Штрих-код
        // Рисуем чуть выше (y = 40f), чтобы сэкономить место.
        // Занимает пространство от 40 до 160 по вертикали (40 + 120).
        val barcodeBmp = BarcodeUtils.generateBarcode(trackingCode, 300, 100)
        canvas.drawBitmap(barcodeBmp, 20f, 40f, paint)

        // 2. Полный трек-номер прямо под штрих-кодом
        paint.textSize = 25f
        paint.isFakeBoldText = false
        // Рисуем на уровне 185 (ровно под штрих-кодом). Сдвинули X на 40, чтобы текст был центричнее.
        canvas.drawText(trackingCode, 20f, 180f, paint)

        // 3. Линия разделения
        paint.strokeWidth = 2f // Делаем линию чуть толще для термопринтера
        // Растягиваем линию от 20 до 380, чтобы она была ровно по ширине штрих-кода (20 + 360 = 380)
        canvas.drawLine(20f, 210f, 380f, 210f, paint)

        // 4. Последние 4 цифры (Крупно)
        paint.textSize = 50f
        paint.isFakeBoldText = true // Включаем жирный шрифт для акцента
        // X=110 сдвигает цифры ближе к центру, Y=280 дает отступ от линии
        canvas.drawText(last4, 90f, 280f, paint)

        // 5. Вес
        paint.textSize = 25f // Чуть крупнее для склада
        paint.isFakeBoldText = false // Возвращаем обычный шрифт
        canvas.drawText("Weight: $weight kg", 20f, 340f, paint)
    }
    fun printParty(
        partialCode: String,
        partialName: String,
        warehouseName: String,
        dateCreated: String,
        boxes: List<BoxInPartial>
    ) {
        if (boxes.isEmpty()) {
            Log.w("PrintHelper", "Нет коробок для печати")
            return
        }

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint()
        paint.color = Color.BLACK

        var y = 40f

        // Заголовок
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("Партия: $partialCode ($partialName)", 40f, y, paint)
        y += 30f

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Склад: $warehouseName | Создано: $dateCreated | Коробок: ${boxes.size}", 40f, y, paint)
        y += 40f

        // Заголовок таблицы
        paint.isFakeBoldText = true
        canvas.drawText("Трек                Размеры         Вес      Объёмный    Заказов", 40f, y, paint)
        y += 20f
        paint.isFakeBoldText = false

        // Строки таблицы
        for (box in boxes) {
            val line = String.format(
                "%-20s %-15s %-8.1f кг  %-10.2f кг  %d",
                box.tracking_code.take(20),
                "${box.width}×${box.height}×${box.length}",
                box.gross_weight,
                box.v_weight,
                box.orderCount
            )
            canvas.drawText(line, 40f, y, paint)
            y += 20f

            if (y > 800) break  // одна страница пока
        }

        // Сумма внизу
        val totalGross = boxes.sumOf { it.gross_weight }
        val totalV = boxes.sumOf { it.v_weight }
        val totalOrders = boxes.sumOf { it.orderCount }

        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Итого:", 40f, y, paint)
        canvas.drawText("Коробок: ${boxes.size}", 300f, y, paint)
        y += 20f
        canvas.drawText("Общий вес: %.2f кг".format(totalGross), 300f, y, paint)
        y += 20f
        canvas.drawText("Объёмный вес: %.2f кг".format(totalV), 300f, y, paint)
        y += 20f
        canvas.drawText("Всего заказов: $totalOrders", 300f, y, paint)

        document.finishPage(page)

        // Сохраняем временный файл
        val fileName = "party_${partialCode.replace("/", "_")}_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.pdf"
        val pdfFile = File(context.cacheDir, fileName)

        try {
            FileOutputStream(pdfFile).use { output ->
                document.writeTo(output)
            }
            Log.d("PrintHelper", "PDF создан: ${pdfFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("PrintHelper", "Ошибка создания PDF", e)
            return
        } finally {
            document.close()
        }

        // Печать через системный PrintManager
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Партия $partialCode - $partialName"
        printManager.print(jobName, MyPrintDocumentAdapter(pdfFile), null)

        // Удаляем файл после печати (опционально)
        pdfFile.deleteOnExit()
    }
}