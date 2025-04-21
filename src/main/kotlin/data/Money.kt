package main.data

import main.utils.SerializationService
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.math.BigDecimal
import java.math.RoundingMode


data class MoneyAmount(val value: BigDecimal) {
    init {
        require(value >= BigDecimal.ZERO) { "Amount must be non-negative" }
    }

    companion object {
        /**
         * Создание MoneyAmount с округлением до 3 знаков после запятой.
         */
        fun apply(value: BigDecimal): MoneyAmount {
            val roundedValue = value.setScale(3, RoundingMode.HALF_UP)
            return MoneyAmount(roundedValue)
        }
    }
}

data class MoneyCurrency(val value: Currency)

data class Money(val moneyAmount: MoneyAmount, val currency: MoneyCurrency)

@WritingConverter
class MoneyToStringConverter(
    private val json: SerializationService
) : Converter<Money, String> {
    override fun convert(source: Money): String =
        json.serializeToString(source)
}

@ReadingConverter
class StringToMoneyConverter(
    private val json: SerializationService
) : Converter<String, Money> {
    override fun convert(source: String): Money =
        json.deserializeFromString(source, Money::class.java)
}