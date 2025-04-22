package main.data

import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.CassandraType.Name
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.stereotype.Repository
import java.io.Serializable
import java.time.Instant
import java.util.*

enum class OrderStatus { NEW, PROCESSING, COMPLETED, CANCELLED, REFUNDED, ON_HOLD, SHIPPED }
enum class DeliveryMethod { PICKUP, DELIVERY, EXPRESS, INTERNATIONAL }
enum class Priority { LOW, MEDIUM, HIGH }

@Table("simple_order_info")
data class SimpleOrderInfo(
    @Id val id: String,
    @CassandraType(type = Name.UDT, userTypeName = "user_profile_type")
    val user: UserProfile,
    @CassandraType(type = Name.UDT, userTypeName = "payment_type")
    val payment: Payment,
    @CassandraType(type = Name.LIST, userTypeName = "product_type")
    val product: List<Product>,
    val quantity: Int,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val totalPrice: Money,
    val orderDate: Long = Instant.now().toEpochMilli(),
    @CassandraType(type = Name.TEXT)
    val status: OrderStatus,
    @CassandraType(type = Name.TEXT)
    val deliveryMethod: DeliveryMethod?,
    val deliveryAddress: String?,
    val estimatedDeliveryDate: Long?,
    val trackingNumber: String?,
    val paymentId: UUID?,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val discountAmount: Money?,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val taxAmount: Money?,
    val metadata: Map<String, String>
) : Serializable

@Repository
interface SimpleOrderInfoRepository : CassandraRepository<SimpleOrderInfo, String>

@Table("cassandra_order_info")
data class CassandraOrderInfo(
    @Id val id: String = UUID.randomUUID().toString(),
    @CassandraType(type = Name.UDT, userTypeName = "user_profile_type")
    val user: UserProfile,
    @CassandraType(type = Name.UDT, userTypeName = "payment_type")
    val payment: Payment,
    @CassandraType(type = Name.LIST, userTypeName = "product_type")
    val product: List<Product>,
    val quantity: Int,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val totalPrice: Money,
    val orderDate: Long = Instant.now().toEpochMilli(),
    val status: OrderStatus,
    val deliveryMethod: DeliveryMethod?,
    val deliveryAddress: String?,
    val estimatedDeliveryDate: Long?,
    val trackingNumber: String?,
    val paymentId: UUID?,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val discountAmount: Money?,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val taxAmount: Money?,
    val metadata: Map<String, String>
) : Serializable

@Repository
interface CassandraOrderInfoRepository : CassandraRepository<CassandraOrderInfo, String>

@Table("precompressed_order_info")
data class PreCompressedOrderInfo(
    @Id val id: String,
    @CassandraType(type = Name.BLOB)
    val compressedPayload: ByteArray
) : Serializable

@Repository
interface PreCompressedOrderInfoRepository : CassandraRepository<PreCompressedOrderInfo, String>

@Table("app_compressed_order_info")
data class CompressedOrderInfo(
    @Id val id: String,
    @CassandraType(type = Name.BLOB)
    val compressedPayload: ByteArray
) : Serializable

@Repository
interface CompressedOrderInfoRepository : CassandraRepository<CompressedOrderInfo, String>