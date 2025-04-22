package main.data

import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.CassandraType.Name
import org.springframework.data.cassandra.core.mapping.UserDefinedType
import java.io.Serializable
import java.util.*


@UserDefinedType("product_type")
data class Product(
    val id: UUID,
    val name: String,
    val description: String?,
    @CassandraType(type = Name.UDT, userTypeName = "money_type")
    val price: Money,
    val stock: Int
) : Serializable