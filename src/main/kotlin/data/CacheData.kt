package main.data

/**
 * Общий класс для типов данных.
 */
sealed class DataType(val name: String) {
    data object Payment : DataType("Payment")
    data object UserProfile : DataType("UserProfile")
    data object OrderInfo : DataType("OrderInfo")
    data object Product : DataType("Product")
    data object JSON : DataType("JSON")
    data object XML : DataType("XML")
    data object Binary : DataType("Binary")
}

/**
 * Универсальная модель данных для сериализации и передачи.
 */
data class CacheData<T>(
    val dataType: DataType,
    val payload: T
)