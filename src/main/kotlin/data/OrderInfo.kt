package main.data

import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.CassandraType.Name
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.stereotype.Repository
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

enum class OrderStatus { NEW, PROCESSING, COMPLETED, CANCELLED, REFUNDED, ON_HOLD, SHIPPED }
enum class DeliveryMethod { PICKUP, DELIVERY, EXPRESS, INTERNATIONAL }
enum class Priority { LOW, MEDIUM, HIGH }

data class OrderInfo(
    @Id
    val id: String = UUID.randomUUID().toString(),
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
data class OnlyPreCompressedOrderInfo(
    @Id val id: String,
    @CassandraType(type = Name.BLOB)
    val compressedPayload: ByteArray
)

@Repository
interface CompressedOrderInfoRepository : CassandraRepository<CompressedOrderInfo, String>

@Repository
interface OnlyPreCompressedOrderInfoRepository :
    CassandraRepository<OnlyPreCompressedOrderInfo, String>

@WritingConverter
class ByteArrayToBlobConverter : Converter<ByteArray, ByteBuffer> {
    override fun convert(source: ByteArray): ByteBuffer =
        ByteBuffer.wrap(source)
}

@ReadingConverter
class BlobToByteArrayConverter : Converter<ByteBuffer, ByteArray> {
    override fun convert(source: ByteBuffer): ByteArray {
        return if (source.hasArray()) {
            source.array()
        } else {
            val bytes = ByteArray(source.remaining())
            source.get(bytes)
            bytes
        }
    }
}