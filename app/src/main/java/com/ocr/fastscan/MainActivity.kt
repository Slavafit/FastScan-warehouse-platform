package com.ocr.fastscan

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.ocr.fastscan.databinding.ActivityMainBinding
import com.ocr.fastscan.ui.ScanFragment
import com.ocr.fastscan.ui.BoxFragment
import com.ocr.fastscan.ui.BatchFragment
import com.ocr.fastscan.R

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // По умолчанию открываем Скан
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, ScanFragment())
            }
        }

        // Обработка переключения вкладок
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragment_container, ScanFragment())
                    }
                    true
                }
                R.id.nav_boxes -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragment_container, BoxFragment())
                    }
                    true
                }
                R.id.nav_batches -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragment_container, BatchFragment())
                    }
                    true
                }
                else -> false
            }
        }
    }
}