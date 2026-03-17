package com.ocr.fastscan.utils

import android.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.SearchResult
import kotlinx.coroutines.launch


class SearchOrder(private val fragment: Fragment) {

    private val firebaseManager = AppDependencies.firebaseManager
    private val context = fragment.requireContext()
    fun showSearchDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_search_order, null)
        // Находим поле ввода напрямую во View диалога
        val input = dialogView.findViewById<EditText>(R.id.etSearchTrack)

        AlertDialog.Builder(context)
            .setTitle("Глобальный поиск")
            .setView(dialogView)
            .setPositiveButton("Найти") { _, _ ->
                val track = input.text.toString().trim()
                if (track.isNotEmpty()) startGlobalSearch(track)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startGlobalSearch(track: String) {
        fragment.lifecycleScope.launch {
            try {
                // показать Toast "Идет поиск..."
                Toast.makeText(context, "Ищем $track...", Toast.LENGTH_SHORT).show()
                val result = firebaseManager.findOrderEverywhere(track)
                displayResults(track, result)
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                //Log.e("SearchOrder", "Ошибка поиска", e)
            }
        }
    }

    private fun displayResults(track: String, result: SearchResult) {
        if (result.shipment == null && result.boxEntry == null) {
            showSimpleDialog("Пусто", "Трек $track не найден.")
            return
        }

        val sb = StringBuilder()
// БЛОК 1: Данные из коллекции shipments (Регистрация)
        if (result.shipment != null) {
            sb.append("📋 РЕГИСТРАЦИЯ:\n")
            sb.append("Клиент: ${result.shipment.clientName} (${result.shipment.clientCode})\n")
            sb.append("Вес: ${result.shipment.weight} кг\n")

            // Создаем нужный формат: день.месяц.год часы:минуты
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())

            // Берем полное время из createdAt и форматируем его
            val formattedDateTime = sdf.format(result.shipment.createdAt.toDate())
            // Используем твое поле dateString для отображения даты
            sb.append("Дата рег.: $formattedDateTime\n\n")
        } else {
            sb.append("❌ Нет данных о регистрации\n\n")
        }

        // БЛОК 2: Данные из коллекции box_entries (Упаковка в коробку)
        if (result.boxEntry != null) {
            sb.append("📦 В КОРОБКЕ:\n")
            sb.append("Номер коробки: ${result.boxEntry.boxTracking}\n")

            // Если при упаковке сохраняется свой вес, можно вывести и его
            if (result.boxEntry.orderWeight > 0.0) {
                sb.append("Фактический вес: ${result.boxEntry.orderWeight} кг\n")
            }

            // Форматируем дату упаковки (если она есть)
            result.boxEntry.date?.let { timestamp ->
                val formattedDate = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(timestamp.toDate())
                sb.append("Упаковано: $formattedDate\n")
            }
        } else {
            sb.append("⚠️ Заказ еще не упакован в коробку")
        }
        showSimpleDialog("Инфо: $track", sb.toString())
    }

    private fun showSimpleDialog(title: String, msg: String) {
        AlertDialog.Builder(context).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }
}