package main.data

import org.springframework.data.cassandra.core.mapping.UserDefinedType
import java.io.Serializable
import java.math.BigDecimal

@UserDefinedType("money_type")
data class Money(
    val moneyAmount: BigDecimal,
    val currency: String
) : Serializable