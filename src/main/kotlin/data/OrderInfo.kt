package main.data

import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.stereotype.Repository
import java.io.Serializable
import java.time.Instant
import java.util.*

@JvmInline
value class OrderId(val value: String) : Serializable

enum class OrderStatus {
    NEW,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    ON_HOLD,
    SHIPPED
}

enum class DeliveryMethod {
    PICKUP,
    DELIVERY,
    EXPRESS,
    INTERNATIONAL
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Модель данных для информации о заказах.
 */
@Table("simple_order_info")
data class OrderInfo(
    @Id val id: OrderId,
    val user: UserProfile,
    val payment: Payment,
    val product: List<Product>,
    val quantity: Int,
    val totalPrice: Money,
    val orderDate: Long = Instant.now().toEpochMilli(),
    val status: OrderStatus,
    val deliveryMethod: DeliveryMethod? = null,
    val deliveryAddress: UserAddress? = null,
    val estimatedDeliveryDate: Long? = null,
    val trackingNumber: String? = null,
    val paymentId: PaymentId? = null,
    val discountAmount: Money? = null,
    val taxAmount: Money? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Table("compressed_order_info")
data class CompressedOrderInfo(
    @Id val id: OrderId,
    val protocol: CompressionType,
    val compressedPayload: ByteArray
)

@Repository
interface OrderInfoRepository : CassandraRepository<OrderInfo, String>

@Repository
interface CompressedOrderInfoRepository : CassandraRepository<CompressedOrderInfo, String>