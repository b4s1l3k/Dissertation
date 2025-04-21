package main.data

import main.utils.SerializationService
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.time.Instant
import java.util.*

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}

enum class PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    PAYPAL,
    BANK_TRANSFER,
    CASH,
    CRYPTOCURRENCY,
    OTHER
}

/**
 * Модель данных для платежей.
 */
data class Payment(
    val id: UUID = UUID.randomUUID(),
    val amount: Money,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val status: PaymentStatus,
    val method: PaymentMethod,
    val description: String? = null,
    val recipientName: String? = null,
    val recipientAccount: Long? = null,
    val senderName: String? = null,
    val senderAccount: Long? = null,
    val transactionFee: Money? = null,
    val taxAmount: Money? = null,
    val metadata: Map<String, String> = emptyMap(),
    val invoiceNumber: Long? = null,
    val confirmationCode: String? = null,
    val scheduledDate: Long? = null,
    val expirationDate: Long? = null,
)

@WritingConverter
class PaymentToStringConverter(
    private val json: SerializationService
) : Converter<Payment, String> {
    override fun convert(source: Payment): String =
        json.serializeToString(source)
}

@ReadingConverter
class StringToPaymentConverter(
    private val json: SerializationService
) : Converter<String, Payment> {
    override fun convert(source: String): Payment =
        json.deserializeFromString(source, Payment::class.java)
}