package main.data

import java.io.Serializable
import java.time.Instant
import java.util.*

@JvmInline
value class PaymentId(val value: UUID) : Serializable

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
    val id: PaymentId = PaymentId(UUID.randomUUID()),
    val amount: Money,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val status: PaymentStatus,
    val method: PaymentMethod,
    val description: String? = null,
    val recipientName: AccountNumber? = null,
    val recipientAccount: AccountNumber? = null,
    val senderName: UserName? = null,
    val senderAccount: AccountNumber? = null,
    val transactionFee: Money? = null,
    val taxAmount: Money? = null,
    val metadata: Map<String, String> = emptyMap(),
    val invoiceNumber: AccountNumber? = null,
    val confirmationCode: String? = null,
    val scheduledDate: Long? = null,
    val expirationDate: Long? = null,
)