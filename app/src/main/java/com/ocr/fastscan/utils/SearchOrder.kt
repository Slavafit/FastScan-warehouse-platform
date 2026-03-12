package com.ocr.fastscan.utils

import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.SearchResult
import kotlinx.coroutines.launch


class SearchOrder(private val fragment: Fragment) {

    private val firebaseManager = AppDependencies.firebaseManager

    //private val firebaseManager = FirebaseManager()

    private val context = fragment.requireContext()

    fun showSearchDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_search_order, null)
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
                val result = firebaseManager.findOrderEverywhere(track)
                displayResults(track, result)
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayResults(track: String, result: SearchResult) {
        if (result.shipment == null && result.boxEntry == null) {
            showSimpleDialog("Пусто", "Трек $track не найден.")
            return
        }

        val sb = StringBuilder()
        result.shipment?.let {
            sb.append("📋 РЕГИСТРАЦИЯ:\nКлиент: ${it.clientName}\nКод: ${it.clientCode}\n\n")
        } ?: sb.append("❌ Нет регистрации\n\n")

        result.boxEntry?.let {
            sb.append("📦 В КОРОБКЕ:\nНомер: ${it.boxTracking}\n")
        } ?: sb.append("⚠️ Не упакован")

        showSimpleDialog("Инфо: $track", sb.toString())
    }

    private fun showSimpleDialog(title: String, msg: String) {
        AlertDialog.Builder(context).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }
}