package main.data

import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.CassandraType.Name
import org.springframework.data.cassandra.core.mapping.UserDefinedType
import java.io.Serializable
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

@UserDefinedType("payment_type")
data class Payment(
    val id: UUID,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val amount: Money,
    val status: String,
    val method: String,
    val description: String?,
    val recipientName: String?,
    val recipientAccount: Long?,
    val senderName: String?,
    val senderAccount: Long?,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val transactionFee: Money,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val taxAmount: Money,
    val metadata: Map<String, String>,
    val invoiceNumber: Long?,
    val confirmationCode: String?,
    val scheduledDate: Long?,
    val expirationDate: Long?
) : Serializable