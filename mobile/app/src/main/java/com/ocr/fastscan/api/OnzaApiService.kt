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
    suspend fun getOrderDetailsBox(@Query("boxId") boxId: String): Response<List<OrderDetails>>

    // Поиск по треку в коробке (возвращает объект OrderDetails)
    @GET("/api/order/order-tracking-code")
    suspend fun getOrderDetails(@Query("tcode") boxId: String): Response<List<OrderDetails>>

    @POST("/api/order/set-user")
    suspend fun saveOrder(@Body data: SaveOrderRequest): Response<SaveResponse>

    // запрос для получения имени и флагов по KG-коду
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
    suspend fun addOrderToBox(@Body request: BoxOrderRequest): Response<List<BoxOrderResponse>>

    @HTTP(method = "DELETE", path = "/api/order/box-order", hasBody = true)
    suspend fun removeOrderFromBox(@Body request: RemoveOrderRequest): Response<RemoveOrderResponse>

    // Список партий. typePartial=0 для открытых, null для всех
    @GET("/api/order/partial")
    suspend fun getPartials(@Query("typePartial") type: Int? = null): Response<List<Partial>>

    // Создать партию
    @POST("/api/order/partial")
    suspend fun createPartial(@Body request: PartialCreateRequest): Response<PartialCreateResponse>

    // Обновить партию (смена статуса и т.д.)
    @POST("/api/order/partial")
    suspend fun updatePartial(@Body request: PartialUpdateRequest): Response<PartialCreateResponse>

    // Отправить партию
    @POST("/api/order/send-partial")
    suspend fun sendPartial(@Body request: SendPartialRequest): Response<SendPartialResponse>

    // Список коробок в партии
    @GET("/api/order/partial-box")
    suspend fun getBoxesInPartial(@Query("guid") guid: String): Response<List<BoxInPartial>>

    // Список открытых коробок (для выбора)
    @GET("/api/order/box")
    suspend fun getOpenBoxes(@Query("status") status: Int = 0): Response<List<BoxInPartial>>  // предполагаем, что возвращает те же поля

    // Привязать коробку к партии
    @POST("/api/order/box")
    suspend fun addBoxToPartial(@Body request: AddBoxRequest): Response<AddBoxResponse>


    // Удаление коробки из партии (пока заглушка — замени на реальный путь)
    //@DELETE("/api/order/partial-box")  // уточни путь
    //suspend fun removeBoxFromPartial(@Query("guid") boxGuid: String, @Query("partial") partialGuid: String): Response<Any>

    @HTTP(method = "DELETE", path = "/api/order/partial-box", hasBody = true)
    suspend fun removeBoxFromPartial(@Body request: PartialCreateRequest): Response<Any>
}