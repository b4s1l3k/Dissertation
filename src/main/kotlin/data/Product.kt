package main.data

import main.utils.SerializationService
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.util.*

/**
 * Модель данных для товаров.
 */
data class Product(
    val id: UUID,
    val name: String,
    val description: String,
    val price: Money,
    val stock: Int
)

@WritingConverter
class ProductListToStringConverter(
    private val json: SerializationService
) : Converter<List<Product>, String> {
    override fun convert(source: List<Product>): String =
        json.serializeToString(source)
}

@ReadingConverter
class StringToProductListConverter(
    private val json: SerializationService
) : Converter<String, List<Product>> {
    override fun convert(source: String): List<Product> =
        json.deserializeFromString(source, Array<Product>::class.java).toList()
}