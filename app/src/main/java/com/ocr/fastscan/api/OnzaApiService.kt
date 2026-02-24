package com.ocr.fastscan.api

import com.ocr.fastscan.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface OnzaApiService {

    @POST("/api/admin/auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @POST("/api/admin/auth/refresh")
    suspend fun refresh(@Body credentials: Map<String, String>): Response<AuthResponse>

    // Поиск по треку (возвращает объект OrderDetails)
    @GET("/api/order/order-tracking-code")
    suspend fun getOrderDetails(@Query("boxId") boxId: String): Response<List<OrderDetails>>

    @POST("/api/order/set-user")
    suspend fun saveOrder(@Body data: SaveOrderRequest): Response<SaveResponse>
    // запрос для получения имени по KG-коду
    @GET("/api/common/reciever")
    suspend fun getReceiverByCode(@Query("client_code") clientCode: String): Response<List<ReceiverResponse>>
    //поиск по имени
    @GET("/api/common/all-reciever")
    suspend fun getAllReceivers(): Response<List<ReceiverItem>>

    // Новые методы для коробок
    @GET("/api/order/box")
    suspend fun getBoxes(
        @Query("guid") guid: String? = "",
        @Query("tCode") tCode: String? = "",
        @Query("status") status: Int? = null  // status=0 для открытых, null для всех
    ): Response<List<BoxItem>>

    @POST("/api/order/box")
    suspend fun createOrUpdateBox(@Body request: BoxRequest): Response<BoxResponse>

    @GET("/api/order/order-search")
    suspend fun searchOrder(@Query("tcode") tcode: String): Response<List<OrderSearchResponse>>

    @POST("/api/order/box-order")
    suspend fun addOrderToBox(@Body request: BoxOrderRequest): Response<BoxOrderResponse>

    @DELETE("/api/order/box-order")
    suspend fun removeOrderFromBox(@Body request: RemoveOrderRequest): Response<RemoveOrderResponse>
}