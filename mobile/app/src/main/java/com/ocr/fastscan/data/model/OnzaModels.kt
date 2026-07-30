package com.ocr.fastscan.data.model

import com.google.gson.annotations.SerializedName
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Для авторизации
data class AuthResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("accessToken")
    val accessToken: String?,  // Именно так возвращает сервер

    @SerializedName("refreshToken")
    val refreshToken: String?, // Именно так возвращает сервер

    @SerializedName("role")
    val role: String?
)
// Данные из GET запроса по треку (приходит массив []) Для получения данных о клиенте
data class OrderDetails(
    @SerializedName("guid") val guid: String?,
    @SerializedName("tracking_number") val trackingNumber: String?,
    @SerializedName("client") val clientName: String?,
    @SerializedName("client_code") val clientCode: String?,
    @SerializedName("with_box") val withBox: String?, // Приходит "1" или null
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("user_guid") val userGuid: String?,
    @SerializedName("length") val length: Int?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)
data class ReceiverResponse(
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("guid")  val recieverGuid: String?,
    @SerializedName("package_shoes")  val shoesBox: Int?,
    @SerializedName("bag_package")  val bagBox: Int?
)

// Ответ при сохранении
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
    // Поле для удобной фильтрации в реестре (например, "2026-02-09")
    val dateString: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
)

// Для отправки финального POST запроса
data class SaveOrderRequest(
    @SerializedName("client_code") val clientCode: String,
    @SerializedName("client") val clientName: String,
    @SerializedName("tracking_number") val trackingNumber: String,
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("with_box") val withBox: Any?,
    @SerializedName("reciever_guid") val recieverGuid: String,
    @SerializedName("user_guid") val userGuid: String,
    @SerializedName("length") val length: Int?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)
//объект только для размеров и веса
data class OrderDimensions(
    val length: Int,
    val width: Int,
    val height: Int,
    val grossWeight: Double
)
//поиск клиента по имени
data class ReceiverItem(
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("client_code") val clientCode: String?
)
// Новые модели для коробок

// Модель для коробки из GET /api/order/box
data class BoxItem(
    @SerializedName("guid") val guid: String?,
    @SerializedName("codeid") val codeid: String?,
    @SerializedName("tracking_code") val trackingCode: String?,
    @SerializedName("warehouse") val warehouse: String?,
    @SerializedName("warehouse_guid") val warehouseGuid: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("length") val length: Int?,
    @SerializedName("v_weight") val vWeight: Double?,
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("date_created") val dateCreated: String?,
    @SerializedName("orderCount") val orderCount: Int?
)

// Запрос для создания/update коробки (POST /api/order/box)
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

// Ответ на POST /api/order/box
data class BoxResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("result") val result: List<BoxResultItem>?  //массив с объектом
)

data class BoxResultItem(
    @SerializedName("NewCodeID") val newCodeID: Int?
)

// Модель для заказа из GET /api/order/order-search?tcode=...
data class OrderSearchResponse(
    @SerializedName("guid") val guid: String?,
    @SerializedName("tracking_number") val trackingNumber: String?,
    @SerializedName("tracking_code") val trackingCode: String?,
    @SerializedName("status_name") val statusName: String?,
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("client_code") val clientCode: String?,
    @SerializedName("client") val client: String?
)

// Запрос для добавления заказа в коробку (POST /api/order/box-order)
data class BoxOrderRequest(
    @SerializedName("order") val order: String,  // guid заказа
    @SerializedName("box") val box: String,  // guid коробки
    @SerializedName("desc") val desc: String = "España"
)

// Ответ на POST /api/order/box-order
data class BoxOrderResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("error") val error: String,
    @SerializedName("guid") val guid: String


    )

// Запрос для удаления заказа из коробки (DELETE /api/order/box-order)
data class RemoveOrderRequest(
    @SerializedName("guid") val guid: String  // guid заказа
)

// Ответ на DELETE /api/order/box-order
data class RemoveOrderResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

// Модель для заказа в коробке (из GET /api/order/order-tracking-code?boxId=...)
data class OrderInBox(
    @SerializedName("guid") val guid: String?,
    @SerializedName("tracking_number") val trackingNumber: String?,
    @SerializedName("gross_weight") val grossWeight: Double?,  // ← добавь это
    @SerializedName("weight") val weight: Double? = null  // если есть отдельное поле weight — оставь, но gross_weight важнее
)

// Модель для записи в Firebase Boxes
data class BoxEntry(
    val orderTracking: String = "",  // трек заказа
    val boxTracking: String = "",  // трек коробки
    val clientCode: String? = null,
    val client: String? = null,
    val date: Timestamp = Timestamp.now(),
    val orderWeight: Double = 0.0
)

//fragment 3. Cajas
// Партия (Partial)
data class Partial(
    val guid: String,
    val codeid: String,
    val partial: String,
    val partial_code: String,
    val partial_name: String,
    val warehouse_name: String,
    val date_created: String,
    val boxCount: Int,
    val status: Int,  // 0 - открыта, 1 - завершена
    val gross_weight: Double? = null
)

// Коробка в партии
data class BoxInPartial(
    val guid: String,
    val codeid: String,
    val partial: String,
    val tracking_code: String,
    val width: Int,
    val height: Int,
    val length: Int,
    val v_weight: Double,
    val gross_weight: Double,
    val date_created: String,
    val warehouse_name: String
)

// Ответ на создание/обновление партии
data class PartialCreateResponse(
    val success: Boolean
)

// Ответ на отправку партии
data class SendPartialResponse(
    val result: Any?  // пустой объект
)
//запрос на сервера при добавлении коробки в партию
data class AddBoxRequest(
    val guid: String,           // guid коробки
    val codeid: String,
    val partial: String,        // guid партии
    val tracking_code: String,
    val width: Int,
    val height: Int,
    val length: Int,
    val v_weight: Double,
    val gross_weight: Double,
)

//ответ сервера при добавлении коробки в партию
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
    val partial: String  // guid партии
)

//поиск по треку
data class SearchResult(
    val shipment: FirestoreShipment? = null,
    val boxEntry: BoxEntry? = null
)
//модель ответа сервера весв
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
