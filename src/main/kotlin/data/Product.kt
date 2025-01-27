package main.data

import java.io.Serializable
import java.util.*

@JvmInline
value class ProductId(val value: UUID) : Serializable

@JvmInline
value class ProductName(val value: String)

@JvmInline
value class ProductDescription(val value: String)

@JvmInline
value class Stock(val value: Int)

/**
 * Модель данных для товаров.
 */
data class Product(
    val id: ProductId,
    val name: ProductName,
    val description: ProductDescription,
    val price: Money,
    val stock: Stock
)
