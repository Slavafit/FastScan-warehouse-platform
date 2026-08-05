package com.ocr.fastscan.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.LifecycleOwner
import com.ocr.fastscan.databinding.DialogVolumeBinding
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.data.model.OrderDimensions
import com.ocr.fastscan.databinding.DialogAddBoxBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val apiService by lazy { AppDependencies.apiService }
private val soundManager by lazy { AppDependencies.soundManager }
object DialogUtils {

    fun showVolumeDialog(
        context: Context,
        onResult: (OrderDimensions) -> Unit
    ) {
        val dialogBinding = DialogVolumeBinding.inflate(LayoutInflater.from(context))

        val builder = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle((R.string.calc_v_weight))

        val dialog = builder.create()

        // Listener for live preview updates
        val textWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                calculateAndShowPreview(dialogBinding)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        // Attach listeners to fields
        dialogBinding.etLength.addTextChangedListener(textWatcher)
        dialogBinding.etWidth.addTextChangedListener(textWatcher)
        dialogBinding.etHeight.addTextChangedListener(textWatcher)

        // Save button logic (btnApplyVolume)
        dialogBinding.btnApplyVolume.setOnClickListener {
            val length = dialogBinding.etLength.text.toString().toIntOrNull() ?: 0
            val width  = dialogBinding.etWidth.text.toString().toIntOrNull()  ?: 0
            val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: 0

            // Validate required fields
            if (length <= 0 || width <= 0 || height <= 0) {
                Toast.makeText(
                    context,
                    (R.string.dimensions_more_0),
                    Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener // Stop execution and keep the dialog open
            }
            // Final calculation; cast one value to Double before division to preserve fractions
            val divider = context.getVolWeightDivider()
            val vWeight = (length.toDouble() * width * height) / divider
            // Format to 2 decimal places
            val vWeightFormatted = String.format(Locale.US,"%.2f", vWeight).toDouble()

            val result = OrderDimensions(
                grossWeight = vWeightFormatted,
                length = length,
                width = width,
                height = height
            )
            //Log.d("SaveOrderJSON", "Sending: $result")

            onResult(result)
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.cancel) { _, _ ->
            dialog.dismiss()
        }
        // Show dialog
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialogBinding.etLength.post {
            dialogBinding.etLength.requestFocus()
            val inputMethodManager =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(dialogBinding.etLength, InputMethodManager.SHOW_IMPLICIT)
        }
    }



    fun showAddNewBoxDialog(
        lifecycleOwner: LifecycleOwner,
        context: Context,
        onSuccess: (String) -> Unit,  // success callback that passes trackingCode
        onError: (String) -> Unit = {}  // failure callback — optional
    ) {
        val dialogBinding = DialogAddBoxBinding.inflate(LayoutInflater.from(context))
        //Log.d("DialogUtils", "Dialog binding created")

        val generatedTrackingCode = generateTrackingCode()
        dialogBinding.tvTrackingCode.text = context.getString(
            R.string.new_box,
            generatedTrackingCode
        )

        //Log.d("DialogUtils", "Generated tracking_code: $generatedTrackingCode")

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle(R.string.add_new_box)
            .setPositiveButton(R.string.create, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialogBinding.etLength.setText("70")
        dialogBinding.etWidth.setText("45")
        dialogBinding.etHeight.setText("50")

        var isSubmitting = false
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton?.setOnClickListener {
                if (isSubmitting) return@setOnClickListener

                val length = dialogBinding.etLength.text.toString().toIntOrNull() ?: 70
                val width = dialogBinding.etWidth.text.toString().toIntOrNull() ?: 45
                val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: 50

                if (length <= 0 || width <= 0 || height <= 0) {
                    Toast.makeText(context, (R.string.dimensions_more_0), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleOwner.lifecycleScope.launch {
                    try {
                        isSubmitting = true
                        positiveButton.isEnabled = false
                        negativeButton?.isEnabled = false
                        dialog.setCanceledOnTouchOutside(false)
                        dialog.setCancelable(false)

                        //Constructor call for passing data
                        val request = BoxRequest(
                            guid = "",
                            codeid = "",
                            trackingCode = generatedTrackingCode,
                            width = width,
                            height = height,
                            length = length,
                            vWeight = (length * width * height) / context.getVolWeightDivider(),
                        )
                        val response = apiService.createOrUpdateBox(request)

                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(context,
                                context.getString(
                                R.string.box_created,
                                generatedTrackingCode),
                                Toast.LENGTH_SHORT)
                                .show()
                            soundManager.playSuccess()
                            onSuccess(generatedTrackingCode)  // pass box code to callback
                            dialog.dismiss()
                        } else {
                            val errorMsg = response.body()?.message ?: context.getString(R.string.error_create)

                            Toast.makeText(context,
                                context.getString(
                                R.string.error_create,
                                errorMsg),
                                Toast.LENGTH_LONG)
                                .show()
                            soundManager.playError()
                            onError(errorMsg)
                        }
                    } catch (e: Exception) {
                        val errorMsg = e.localizedMessage
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.net_error,
                                errorMsg),
                            Toast.LENGTH_LONG)
                            .show()
                        soundManager.playError()
                        onError(e.localizedMessage ?: context.getString(R.string.unknown_error))
                    } finally {
                        if (dialog.isShowing) {
                            isSubmitting = false
                            positiveButton.isEnabled = true
                            negativeButton?.isEnabled = true
                            dialog.setCanceledOnTouchOutside(true)
                            dialog.setCancelable(true)
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    // Code generation function; keep it here if it was moved from the fragment
    private fun generateTrackingCode(): String {
        val date = SimpleDateFormat("MMdd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())
        val random = (0..9999).random().toString().padStart(4, '0')
        return "NBX$date$time$random"
    }
}
// Separate function for calculation and preview display
private fun calculateAndShowPreview(binding: DialogVolumeBinding) {
    val length = binding.etLength.text.toString().toIntOrNull() ?: 0
    val width  = binding.etWidth.text.toString().toIntOrNull()  ?: 0
    val height = binding.etHeight.text.toString().toIntOrNull() ?: 0

    if (length > 0 && width > 0 && height > 0) {
        val divider = binding.root.context.getVolWeightDivider()
        val vWeight = (length * width * height / divider)
        //binding.tvResultPreview.text = String.format(java.util.Locale.US, "Weight: %.2f kg", vWeight)

        binding.tvResultPreview.text = binding.root.context.getString(R.string.weight_format, vWeight)
    } else {
        binding.tvResultPreview.text = binding.root.context.getString(R.string.weight_kg)
    }
}
