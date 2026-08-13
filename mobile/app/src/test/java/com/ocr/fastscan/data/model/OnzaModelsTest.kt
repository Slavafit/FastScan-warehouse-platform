package com.ocr.fastscan.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

class OnzaModelsTest {
    @Test
    fun `batch list response keeps guid and partial as separate fields`() {
        val json = """
            [{
              "guid": "54a71d9e-cdb6-42c7-a936-a0d9560141c6",
              "codeid": "3",
              "partial": "54a71d9e-cdb6-42c7-a936-a0d9560141c6",
              "partial_code": "NPX081310724640",
              "partial_name": "KG001",
              "warehouse_name": "BMG",
              "date_created": "2026-08-13T08:51:00.373Z",
              "boxCount": 0,
              "status": 0,
              "gross_weight": 0
            }]
        """.trimIndent()

        val type = object : TypeToken<List<Partial>>() {}.type
        val batches: List<Partial> = Gson().fromJson(json, type)

        assertEquals(1, batches.size)
        assertEquals("54a71d9e-cdb6-42c7-a936-a0d9560141c6", batches.single().guid)
        assertEquals(batches.single().guid, batches.single().partial)
        assertEquals("KG001", batches.single().partial_name)
    }
}
