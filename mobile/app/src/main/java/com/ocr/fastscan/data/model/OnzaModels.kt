package com.ocr.fastscan.data.model

import com.google.gson.annotations.SerializedName
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// For authentication
data class AuthResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("accessToken")
    val accessToken: String?,  // This is the exact server response field

    @SerializedName("refreshToken")
    val refreshToken: String?, // This is the exact server response field

    @SerializedName("role")
    val role: String?
)

data class ApiHealthResponse(
    @SerializedName("status") val status: String?
)
// Data from GET by tracking code; arrives as an array and includes client data
data class OrderDetails(
    @SerializedName("guid") val guid: String?,
    @SerializedName("tracking_number") val trackingNumber: String?,
    @SerializedName("client") val clientName: String?,
    @SerializedName("client_code") val clientCode: String?,
    @SerializedName("with_box") val withBox: String?, // Returns "1" or null
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("user_guid") val userGuid: String?,
    @SerializedName("length") val length: Int?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)
data class ReceiverResponse(
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("guid") val receiverGuid: String?,
    @SerializedName("package_shoes")  val shoesBox: Int?,
    @SerializedName("bag_package")  val bagBox: Int?
)

// Save response
data class SaveResponse(
    val success: Boolean
)
data class FirestoreShipment(
    val trackingNumber: String = "",
    val clientCode: String = "",
    val clientName: String = "",
    val weight: Double = 0.0,
    val withBox: String = "null",
    val createdAt: Timestamp = Timestamp.now(),
    // Field for convenient registry filtering, for example "2026-02-09"
    val dateString: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
)

// For the final POST request
data class SaveOrderRequest(
    @SerializedName("client_code") val clientCode: String,
    @SerializedName("client") val clientName: String,
    @SerializedName("tracking_number") val trackingNumber: String,
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("with_box") val withBox: Any?,
    @SerializedName("receiver_guid") val receiverGuid: String,
    @SerializedName("user_guid") val userGuid: String,
    @SerializedName("length") val length: Int?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)
//Object for dimensions and weight only
data class OrderDimensions(
    val length: Int,
    val width: Int,
    val height: Int,
    val grossWeight: Double
)
// Search client by name
data class ReceiverItem(
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("client_code") val clientCode: String?
)
// New box models

// Box model from GET /api/mobile/boxes
data class BoxItem(
    @SerializedName("guid") val guid: String?,
    @SerializedName("codeid") val codeid: String?,
    @SerializedName("tracking_code") val trackingCode: String?,
    @SerializedName("warehouse") val warehouse: String?,
    @SerializedName("warehouse_guid") val warehouseGuid: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("length") val length: Int?,
    @SerializedName(value = "v_weight", alternate = ["volume_weight"]) val vWeight: Double?,
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName(value = "date_created", alternate = ["created_at"]) val dateCreated: String?,
    @SerializedName(value = "orderCount", alternate = ["orders_count"]) val orderCount: Int?
)

// Request to create/update a box via POST /api/mobile/boxes
data class BoxRequest(
    @SerializedName("guid") val guid: String? = null,
    @SerializedName("codeid") val codeid: String?,
    @SerializedName("tracking_code") val trackingCode: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("length") val length: Int,
    @SerializedName("v_weight") val vWeight: Double = 0.0,
    @SerializedName("gross_weight") val weight: Double? = null,
)

// Response from POST /api/mobile/boxes
data class BoxResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("result") val result: List<BoxResultItem>?  //array with an object
)

data class BoxResultItem(
    @SerializedName("NewCodeID") val newCodeID: Int?
)

// Order model from GET /api/mobile/orders?tracking_number=...
data class OrderSearchResponse(
    @SerializedName("guid") val guid: String?,
    @SerializedName("tracking_number") val trackingNumber: String?,
    @SerializedName("tracking_code") val trackingCode: String?,
    @SerializedName("status_name") val statusName: String?,
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("client_code") val clientCode: String?,
    @SerializedName("client") val client: String?
)

// Request to add an order to a box via POST /api/mobile/boxes/orders
data class BoxOrderRequest(
    @SerializedName("order") val order: String,  // order guid
    @SerializedName("box") val box: String,  // box guid
    @SerializedName("desc") val desc: String = "España"
)

// Response from POST /api/mobile/boxes/orders
data class BoxOrderResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("error") val error: String,
    @SerializedName("guid") val guid: String


    )

// Request to remove an order from a box via DELETE /api/mobile/boxes/orders
data class RemoveOrderRequest(
    @SerializedName("guid") val guid: String  // order guid
)

// Response from DELETE /api/mobile/boxes/orders
data class RemoveOrderResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

// Order-in-box model from GET /api/mobile/orders?box_tracking_code=...
data class OrderInBox(
    @SerializedName("guid") val guid: String?,
    @SerializedName("tracking_number") val trackingNumber: String?,
    @SerializedName("gross_weight") val grossWeight: Double?,  // keep this field
    @SerializedName("weight") val weight: Double? = null  // keep weight if it exists, but gross_weight is more important
)

// Model for Firebase box entries
data class BoxEntry(
    val orderTracking: String = "",  // order tracking code
    val boxTracking: String = "",  // box tracking code
    val clientCode: String? = null,
    val client: String? = null,
    val date: Timestamp = Timestamp.now(),
    val orderWeight: Double = 0.0
)

//fragment 3. Cajas
// Batch (partial)
data class Partial(
    val guid: String,
    val codeid: String,
    @SerializedName(value = "partial", alternate = ["guid"]) val partial: String,
    @SerializedName(value = "partial_code", alternate = ["batch_code"]) val partial_code: String,
    @SerializedName(value = "partial_name", alternate = ["batch_name"]) val partial_name: String,
    val warehouse_name: String,
    @SerializedName(value = "date_created", alternate = ["created_at"]) val date_created: String,
    @SerializedName(value = "boxCount", alternate = ["boxes_count"]) val boxCount: Int,
    val status: Int,  // 0 - open, 1 - completed
    val gross_weight: Double? = null
)

// Box in batch
data class BoxInPartial(
    val guid: String,
    val codeid: String,
    @SerializedName(value = "partial", alternate = ["batch_guid"]) val partial: String,
    val tracking_code: String,
    val width: Int,
    val height: Int,
    val length: Int,
    @SerializedName(value = "v_weight", alternate = ["volume_weight"]) val v_weight: Double,
    val gross_weight: Double,
    @SerializedName(value = "date_created", alternate = ["created_at"]) val date_created: String,
    val warehouse_name: String
)

// Create/update batch response
data class PartialCreateResponse(
    val success: Boolean
)

// Send batch response
data class SendPartialResponse(
    val result: Any?  // empty object
)
//Server request for adding a box to a batch
data class AddBoxRequest(
    val guid: String,           // box guid
    val codeid: String,
    val partial: String,        // batch guid
    val tracking_code: String,
    val width: Int,
    val height: Int,
    val length: Int,
    val v_weight: Double,
    val gross_weight: Double,
)

//Server response after adding a box to a batch
data class AddBoxResponse(
    val success: Boolean,
    val message: String,
    val result: List<UpdatedCodeResult>?
)

data class UpdatedCodeResult(
    val UpdatedCodeID: String
)

data class PartialCreateRequest(
    val partial_code: String,
    val partial_name: String
)

data class PartialUpdateRequest(
    val guid: String,
    val partial_code: String,
    val partial_name: String,
    val status: Int
)

data class SendPartialRequest(
    val partial: String  // batch guid
)

//Search by tracking code
data class SearchResult(
    val shipment: FirestoreShipment? = null,
    val boxEntry: BoxEntry? = null
)
//Weight server response model
data class WeightResponse(
    val weight: Double,
    val raw: String = "",
    val unit: String = "kg"
)

data class UnpackedOrder(
    val trackingNumber: String,
    val clientName: String,
    val clientCode: String,
    val grossWeight: Double,
    val registeredAt: Timestamp
)
