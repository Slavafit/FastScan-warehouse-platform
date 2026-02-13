package com.ocr.fastscan.api

import com.ocr.fastscan.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface OnzaApiService {

    @POST("/api/admin/auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @POST("/api/admin/auth/refresh")
    suspend fun refresh(@Body credentials: Map<String, String>): Response<AuthResponse>

    // Сценарий А: Поиск по треку (возвращает объект OrderDetails)
    @GET("/api/order/order-tracking-code")
    suspend fun getOrderDetails(@Query("tcode") tcode: String): Response<List<OrderDetails>>

    @POST("/api/order/set-user")
    suspend fun saveOrder(@Body data: SaveOrderRequest): Response<SaveResponse>
    // запрос для получения имени по KG-коду
    @GET("/api/common/reciever")
    suspend fun getReceiverByCode(@Query("client_code") clientCode: String): Response<ReceiverResponse>





}