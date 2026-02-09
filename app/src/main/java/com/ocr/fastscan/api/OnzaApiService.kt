package com.ocr.fastscan.api

import com.ocr.fastscan.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface OnzaApiService {

    @POST("/api/admin/auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @GET("/api/order/order-tracking-code")
    suspend fun getOrderDetails(@Query("tcode") tcode: String): Response<List<OrderDetails>>

    @POST("/api/order/set-user")
    suspend fun saveOrder(@Body data: SaveOrderRequest): Response<SaveResponse>
}