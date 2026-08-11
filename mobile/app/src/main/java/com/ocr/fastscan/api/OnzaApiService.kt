package com.ocr.fastscan.api

import com.ocr.fastscan.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface OnzaApiService {

    @GET("/health/ready")
    suspend fun getApiHealth(): Response<ApiHealthResponse>

    @POST("/api/mobile/auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @POST("/api/mobile/auth/refresh")
    suspend fun refresh(@Body credentials: Map<String, String>): Response<AuthResponse>

    // Search by tracking code; returns OrderDetails
    @GET("/api/mobile/orders")
    suspend fun getOrderDetailsBox(@Query("box_tracking_code") boxId: String): Response<List<OrderDetails>>

    // Search by tracking code inside a box; returns OrderDetails
    @GET("/api/mobile/orders")
    suspend fun getOrderDetails(@Query("tracking_number") boxId: String): Response<List<OrderDetails>>

    @POST("/api/mobile/orders")
    suspend fun saveOrder(@Body data: SaveOrderRequest): Response<SaveResponse>

    // Request name and flags by KG code
    @GET("/api/mobile/receivers")
    suspend fun getReceiverByCode(@Query("client_code") clientCode: String): Response<List<ReceiverResponse>>
    //Search by name
    @GET("/api/mobile/receivers")
    suspend fun getAllReceivers(): Response<List<ReceiverItem>>

    // New box methods
    @GET("/api/mobile/boxes")
    suspend fun getBoxes(
        @Query("guid") guid: String? = "",
        @Query("tracking_code") tCode: String? = "",
        @Query("status") status: Int? = null  // status=0 for open items, null for all
    ): Response<List<BoxItem>>

    @POST("/api/mobile/boxes")
    suspend fun createOrUpdateBox(@Body request: BoxRequest): Response<BoxResponse>

    @GET("/api/mobile/orders")
    suspend fun searchOrder(@Query("tracking_number") tcode: String): Response<List<OrderSearchResponse>>

    @POST("/api/mobile/boxes/orders")
    suspend fun addOrderToBox(@Body request: BoxOrderRequest): Response<List<BoxOrderResponse>>

    @HTTP(method = "DELETE", path = "/api/mobile/boxes/orders", hasBody = true)
    suspend fun removeOrderFromBox(@Body request: RemoveOrderRequest): Response<RemoveOrderResponse>

    // Batch list. status=0 for open batches, null for all
    @GET("/api/mobile/batches")
    suspend fun getPartials(@Query("status") type: Int? = null): Response<List<Partial>>

    // Create batch
    @POST("/api/mobile/batches")
    suspend fun createPartial(@Body request: PartialCreateRequest): Response<PartialCreateResponse>

    // Update batch, including status changes
    @POST("/api/mobile/batches")
    suspend fun updatePartial(@Body request: PartialUpdateRequest): Response<PartialCreateResponse>

    // Send batch
    @POST("/api/mobile/batches/complete")
    suspend fun sendPartial(@Body request: SendPartialRequest): Response<SendPartialResponse>

    // List boxes in a batch
    @GET("/api/mobile/batches/boxes")
    suspend fun getBoxesInPartial(@Query("guid") guid: String): Response<List<BoxInPartial>>

    // List open boxes for selection
    @GET("/api/mobile/boxes")
    suspend fun getOpenBoxes(@Query("status") status: Int = 0): Response<List<BoxInPartial>>  // Assume it returns the same fields

    // Attach box to batch
    @POST("/api/mobile/batches/boxes")
    suspend fun addBoxToPartial(@Body request: AddBoxRequest): Response<AddBoxResponse>

    @HTTP(method = "DELETE", path = "/api/mobile/batches/boxes", hasBody = true)
    suspend fun removeBoxFromPartial(@Body request: PartialCreateRequest): Response<Any>
}
