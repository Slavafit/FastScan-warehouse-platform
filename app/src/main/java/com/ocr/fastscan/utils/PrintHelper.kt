package com.ocr.fastscan.utils

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.ocr.fastscan.R
import androidx.preference.PreferenceManager
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

class PrintHelper(private val activity: Activity) {

    private val context: Context = activity
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    /**
     * Прямая печать этикетки заказа (ZPL)
     */
    fun printLabel(
        trackingNumber: String,
        clientCode: String,
        client: String,
        weight: String
    ) {
        val printerIp = getPrinterIp()
        if (printerIp.isEmpty()) {
            showToast(context.getString(R.string.printer_ip_error))
            return
        }

        val widthDots = getPrinterWidth() * 8
        val heightDots = getPrinterHeight() * 8

        val zpl = """
            ^XA
            ^PW$widthDots
            ^LL$heightDots
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
            ^FO80,480^FDGross weight: $weight kg^FS
            ^XZ
        """.trimIndent()
        
        sendToPrinter(printerIp, zpl)
    }

    /**
     * Прямая печать этикетки коробки (ZPL)
     */
    fun printBoxLabel(
        trackingNumber: String,
        weight: String,
        last4: String,
        partialName: String,
        length: String,
        width: String,
        height: String
    ) {
        val printerIp = getPrinterIp()
        if (printerIp.isEmpty()) {
            showToast(context.getString(R.string.printer_ip_error))
            return
        }

        val widthDots = getPrinterWidth() * 8
        val heightDots = getPrinterHeight() * 8
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        
        val zpl = """
            ^XA
            ^PW$widthDots
            ^LL$heightDots
            ^CI28
            ^CF0,50
            ^FO80,40^FDOnza España^FS
            ^BY3,3,120
            ^FO80,110^BCN,120,Y,N,N^FD$trackingNumber^FS
            ^FO80,330^A0N,80,80^FD$last4^FS
            ^FO80,420^GB600,3,3^FS
            ^CF0,30
            ^FO80,440^FDPrinted en Malaga: $dateStr^FS
            ^FO80,480^GB600,3,3^FS
            ^FO80,510^A0N,120,120^FD$partialName^FS
            ^CF0,50
            ^FO80,660^FDGross weight total: $weight kg^FS
            ^CF0,50
            ^FO80,760^FDSizes: ${length}x${width}x$height cm^FS
            ^FO80,840^BQN,2,10^FDQA$trackingNumber^FS
            ^XZ
        """.trimIndent()
        
        sendToPrinter(printerIp, zpl)
    }

    /**
     * Тестовая печать для настроек
     */
    fun printTestLabel() {
        val printerIp = getPrinterIp()
        if (printerIp.isEmpty()) {
            showToast(context.getString(R.string.printer_ip_error))
            return
        }

        val widthMm = getPrinterWidth()
        val heightMm = getPrinterHeight()
        val widthDots = widthMm * 8
        val heightDots = heightMm * 8

        val testZpl = """
            ^XA
            ^PW$widthDots
            ^LL$heightDots
            ^CI28
            ^CF0,60
            ^FO80,50^FDTEST PRINT^FS
            ^FO80,120^FDZebra ZPL Direct^FS
            ^BY3,3,100
            ^FO80,200^BCN,100,Y,N,N^FD12345678^FS
            ^CF0,40
            ^FO80,350^FDIP: $printerIp^FS
            ^FO80,400^FDSize: ${widthMm}x${heightMm}mm^FS
            ^FO80,450^FDStatus: OK^FS
            ^XZ
        """.trimIndent()
        
        sendToPrinter(printerIp, testZpl)
    }

    private fun getPrinterIp(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("printer_ip", "192.168.1.168") ?: "192.168.1.168"
    }

    private fun getPrinterWidth(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val widthStr = prefs.getString("printer_width", "100") ?: "100"
        return widthStr.toIntOrNull() ?: 100
    }

    private fun getPrinterHeight(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val heightStr = prefs.getString("printer_height", "150") ?: "150"
        return heightStr.toIntOrNull() ?: 150
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
                    showToast(context.getString(R.string.test_print_result, ip))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(context.getString(R.string.test_print_error, e.message))
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
