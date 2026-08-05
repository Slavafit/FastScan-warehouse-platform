package com.ocr.fastscan.api

import com.ocr.fastscan.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface OnzaApiService {

    @POST("/api/admin/auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @POST("/api/admin/auth/refresh")
    suspend fun refresh(@Body credentials: Map<String, String>): Response<AuthResponse>

    // Search by tracking code; returns OrderDetails
    @GET("/api/order/order-tracking-code")
    suspend fun getOrderDetailsBox(@Query("boxId") boxId: String): Response<List<OrderDetails>>

    // Search by tracking code inside a box; returns OrderDetails
    @GET("/api/order/order-tracking-code")
    suspend fun getOrderDetails(@Query("tcode") boxId: String): Response<List<OrderDetails>>

    @POST("/api/order/set-user")
    suspend fun saveOrder(@Body data: SaveOrderRequest): Response<SaveResponse>

    // Request name and flags by KG code
    @GET("/api/common/reciever")
    suspend fun getReceiverByCode(@Query("client_code") clientCode: String): Response<List<ReceiverResponse>>
    //Search by name
    @GET("/api/common/all-reciever")
    suspend fun getAllReceivers(): Response<List<ReceiverItem>>

    // New box methods
    @GET("/api/order/box")
    suspend fun getBoxes(
        @Query("guid") guid: String? = "",
        @Query("tCode") tCode: String? = "",
        @Query("status") status: Int? = null  // status=0 for open items, null for all
    ): Response<List<BoxItem>>

    @POST("/api/order/box")
    suspend fun createOrUpdateBox(@Body request: BoxRequest): Response<BoxResponse>

    @GET("/api/order/order-search")
    suspend fun searchOrder(@Query("tcode") tcode: String): Response<List<OrderSearchResponse>>

    @POST("/api/order/box-order")
    suspend fun addOrderToBox(@Body request: BoxOrderRequest): Response<List<BoxOrderResponse>>

    @HTTP(method = "DELETE", path = "/api/order/box-order", hasBody = true)
    suspend fun removeOrderFromBox(@Body request: RemoveOrderRequest): Response<RemoveOrderResponse>

    // Batch list. typePartial=0 for open batches, null for all
    @GET("/api/order/partial")
    suspend fun getPartials(@Query("typePartial") type: Int? = null): Response<List<Partial>>

    // Create batch
    @POST("/api/order/partial")
    suspend fun createPartial(@Body request: PartialCreateRequest): Response<PartialCreateResponse>

    // Update batch, including status changes
    @POST("/api/order/partial")
    suspend fun updatePartial(@Body request: PartialUpdateRequest): Response<PartialCreateResponse>

    // Send batch
    @POST("/api/order/send-partial")
    suspend fun sendPartial(@Body request: SendPartialRequest): Response<SendPartialResponse>

    // List boxes in a batch
    @GET("/api/order/partial-box")
    suspend fun getBoxesInPartial(@Query("guid") guid: String): Response<List<BoxInPartial>>

    // List open boxes for selection
    @GET("/api/order/box")
    suspend fun getOpenBoxes(@Query("status") status: Int = 0): Response<List<BoxInPartial>>  // Assume it returns the same fields

    // Attach box to batch
    @POST("/api/order/box")
    suspend fun addBoxToPartial(@Body request: AddBoxRequest): Response<AddBoxResponse>


    // Remove box from batch; placeholder until the real path is confirmed
    //@DELETE("/api/order/partial-box")  // confirm the path
    //suspend fun removeBoxFromPartial(@Query("guid") boxGuid: String, @Query("partial") partialGuid: String): Response<Any>

    @HTTP(method = "DELETE", path = "/api/order/partial-box", hasBody = true)
    suspend fun removeBoxFromPartial(@Body request: PartialCreateRequest): Response<Any>
}