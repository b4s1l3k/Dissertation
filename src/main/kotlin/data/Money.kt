package main.data

import java.math.BigDecimal
import java.math.RoundingMode


@JvmInline
value class MoneyAmount(val value: BigDecimal) {
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

@JvmInline
value class MoneyCurrency(val value: Currency)


data class Money(val moneyAmount: MoneyAmount, val currency: MoneyCurrency)
