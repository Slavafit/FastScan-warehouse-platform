package com.ocr.fastscan.utils

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.R
import kotlinx.coroutines.launch


class SearchOrder(private val fragment: Fragment) {

    private val apiService by lazy { AppDependencies.apiService }
    private val context = fragment.requireContext()
    
    fun showSearchDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_search_order, null)
        val input = dialogView.findViewById<EditText>(R.id.etSearchTrack)

        AlertDialog.Builder(context)
            .setTitle(R.string.title_find)
            .setView(dialogView)
            .setPositiveButton(R.string.find) { _, _ ->
                hideKeyboard(input) // Hide keyboard when starting search
                val track = input.text.toString().trim()
                if (track.isNotEmpty()) startGlobalSearch(track)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startGlobalSearch(track: String) {
        fragment.lifecycleScope.launch {
            try {
                Toast.makeText(context, context.getString(R.string.finding, track), Toast.LENGTH_SHORT).show()
                val response = apiService.searchOrder(track)
                if (!response.isSuccessful) {
                    throw IllegalStateException("API ${response.code()}")
                }
                displayResults(track, response.body().orEmpty().firstOrNull())
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.error, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayResults(track: String, order: com.ocr.fastscan.data.model.OrderSearchResponse?) {
        if (order == null) {
            showSimpleDialog(
            context.getString(R.string.empty),
            context.getString(R.string.no_founded, track ))
            return
        }

        val sb = StringBuilder()
        sb.append(context.getString(R.string.registration_title)).append("\n")
        sb.append(context.getString(R.string.client))
            .append(": ${order.client ?: "-"} (${order.clientCode ?: "-"})\n")
        sb.append(context.getString(R.string.weight_format, order.grossWeight ?: 0.0)).append("\n")
        order.createdAt?.let { sb.append(context.getString(R.string.reg_date, readableDate(it))).append("\n") }
        sb.append("\n")

        if (!order.boxTrackingCode.isNullOrBlank()) {
            sb.append(context.getString(R.string.in_box_title)).append("\n")
            sb.append(context.getString(R.string.box_number, order.boxTrackingCode)).append("\n")
        } else {
            sb.append(context.getString(R.string.not_packed_yet))
        }
        showSimpleDialog(
            context.getString(R.string.dialog_title_info, track),
            sb.toString()
           )
    }

    private fun readableDate(value: String): String = value
        .replace('T', ' ')
        .substringBefore('.')
        .removeSuffix("Z")

    private fun showSimpleDialog(title: String, msg: String) {
        AlertDialog.Builder(context).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
