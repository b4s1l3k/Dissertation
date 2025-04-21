package main.data

import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.CassandraType.Name
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

enum class OrderStatus { NEW, PROCESSING, COMPLETED, CANCELLED, REFUNDED, ON_HOLD, SHIPPED }
enum class DeliveryMethod { PICKUP, DELIVERY, EXPRESS, INTERNATIONAL }
enum class Priority { LOW, MEDIUM, HIGH }

@Table("simple_order_info")
data class SimpleOrderInfo(
    @Id val id: String,
    @CassandraType(type = Name.TEXT) val user: UserProfile,
    @CassandraType(type = Name.TEXT) val payment: Payment,
    @CassandraType(type = Name.TEXT) val product: List<Product>,
    val quantity: Int,
    @CassandraType(type = Name.TEXT) val totalPrice: Money,
    val orderDate: Long = Instant.now().toEpochMilli(),
    val status: OrderStatus,
    val deliveryMethod: DeliveryMethod?,
    val deliveryAddress: String?,
    val estimatedDeliveryDate: Long?,
    val trackingNumber: String?,
    val paymentId: UUID?,
    @CassandraType(type = Name.TEXT) val discountAmount: Money?,
    @CassandraType(type = Name.TEXT) val taxAmount: Money?,
    val metadata: Map<String, String>
)

@Table("cassandra_order_info")
data class CassandraOrderInfo(
    @Id val id: String = UUID.randomUUID().toString(),
    @CassandraType(type = Name.TEXT)
    val user: UserProfile,
    @CassandraType(type = Name.TEXT)
    val payment: Payment,
    @CassandraType(type = Name.TEXT)
    val product: List<Product>,
    val quantity: Int,
    @CassandraType(type = Name.TEXT)
    val totalPrice: Money,
    val orderDate: Long = Instant.now().toEpochMilli(),
    val status: OrderStatus,
    val deliveryMethod: DeliveryMethod? = null,
    val deliveryAddress: String? = null,
    val estimatedDeliveryDate: Long? = null,
    val trackingNumber: String? = null,
    val paymentId: UUID? = null,
    @CassandraType(type = Name.TEXT)
    val discountAmount: Money? = null,
    @CassandraType(type = Name.TEXT)
    val taxAmount: Money? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Table("compressed_order_info")
data class CompressedOrderInfo(
    @Id val id: String,
    @CassandraType(type = Name.BLOB)
    val compressedPayload: ByteArray
)

@Table("precompressed_order_info")
data class PreCompressedOrderInfo(
    @Id val id: String,
    @CassandraType(type = Name.BLOB)
    val compressedPayload: ByteArray
)

@Repository
interface SimpleOrderInfoRepository : CassandraRepository<SimpleOrderInfo, String>

@Repository
interface CassandraOrderInfoRepository : CassandraRepository<CassandraOrderInfo, String>

@Repository
interface CompressedOrderInfoRepository : CassandraRepository<CompressedOrderInfo, String>

@Repository
interface PreCompressedOrderInfoRepository : CassandraRepository<PreCompressedOrderInfo, String>