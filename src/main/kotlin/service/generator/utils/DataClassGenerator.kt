package main.service.generator.utils

import io.github.serpro69.kfaker.Faker
import main.data.*
import main.data.Currency
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.*
import kotlin.collections.List
import kotlin.random.Random

interface DataClassGenerator {
    fun generateUserProfile(): UserProfile
    fun generatePayment(): Payment
    fun generateOrderInfo(): OrderInfo
}

@Component
class DataClassGeneratorImpl : DataClassGenerator {
    private val faker = Faker()

    /**
     * Генерация случайного заказа.
     */
    override fun generateOrderInfo(): OrderInfo {
        return OrderInfo(
            id = OrderId(Random.nextInt().toString()),
            user = generateUserProfile(),
            payment = generatePayment(),
            product = generateProducts(),
            quantity = Random.nextInt(1, 10),
            totalPrice = Money(
                moneyAmount = MoneyAmount(
                    BigDecimal(Random.nextDouble(10.0, 1000.0)).setScale(
                        3,
                        RoundingMode.HALF_UP
                    )
                ),
                currency = MoneyCurrency(Currency.entries.toTypedArray().random())
            ),
            status = OrderStatus.entries.toTypedArray().random(),
            deliveryMethod = DeliveryMethod.entries.toTypedArray().randomOrNull(),
            deliveryAddress = nullOrAddress()?.let { UserAddress(it) },
            estimatedDeliveryDate = nullOrTime(),
            trackingNumber = nullOrString(),
            paymentId = PaymentId(UUID.randomUUID()),
            discountAmount = nullOrMoney(),
            taxAmount = nullOrMoney(),
            metadata = mapOf(
                "note" to nullOrString().orEmpty(),
                "priority" to Priority.entries.toTypedArray().random().toString()
            )
        )
    }

    /**
     * Генерация случайного профиля пользователя.
     */
    override fun generateUserProfile(): UserProfile {
        return UserProfile(
            id = UserId(UUID.randomUUID().toString()),
            name = UserName(faker.name.name()),
            email = UserEmail(faker.internet.email()),
            phone = nullOrPhone()?.let { UserPhone(it) },
            address = nullOrAddress()?.let { UserAddress(it) },
            birthDate = nullOrTime(),
            gender = Gender.entries.toTypedArray().randomOrNull(),
            createdAt = Instant.now().toEpochMilli(),
            lastUpdatedAt = Instant.now().toEpochMilli(),
            preferences = generatePreferences()
        )
    }

    /**
     * Генерация случайного платежа.
     */
    override fun generatePayment(): Payment {
        return Payment(
            id = PaymentId(UUID.randomUUID()),
            amount = Money(
                moneyAmount = MoneyAmount(
                    BigDecimal(Random.nextDouble(10.0, 1000.0)).setScale(
                        3,
                        RoundingMode.HALF_UP
                    )
                ),
                currency = MoneyCurrency(Currency.entries.toTypedArray().random())
            ),
            status = PaymentStatus.entries.toTypedArray().random(),
            method = PaymentMethod.entries.toTypedArray().random(),
            description = nullOrString(),
            recipientName = nullOrName()?.let { AccountNumber(it) },
            recipientAccount = nullOrLong()?.let { AccountNumber(it.toString()) },
            senderName = nullOrName()?.let { UserName(it) },
            senderAccount = nullOrLong()?.let { AccountNumber(it.toString()) },
            transactionFee = Money(
                moneyAmount = MoneyAmount(
                    BigDecimal(Random.nextDouble(10.0, 1000.0)).setScale(
                        3,
                        RoundingMode.HALF_UP
                    )
                ),
                currency = MoneyCurrency(Currency.entries.toTypedArray().random())
            ),
            taxAmount = Money(
                moneyAmount = MoneyAmount(
                    BigDecimal(Random.nextDouble(10.0, 1000.0)).setScale(
                        3,
                        RoundingMode.HALF_UP
                    )
                ),
                currency = MoneyCurrency(Currency.entries.toTypedArray().random())
            ),
            metadata = mapOf(
                "order_id" to UUID.randomUUID().toString(),
                "note" to nullOrString().orEmpty()
            ),
            invoiceNumber = nullOrLong()?.let { AccountNumber(it.toString()) },
            confirmationCode = nullOrString(),
            scheduledDate = nullOrTime(),
            expirationDate = nullOrTime()
        )
    }

    /**
     * Генерация случайного продукта.
     */
    fun generateProducts(): List<Product> {
        return List(Random.nextInt(1, 100)) {
            Product(
                id = ProductId(UUID.randomUUID()),
                name = ProductName(faker.commerce.productName()),
                description = ProductDescription(faker.fallout.quotes()),
                price = Money(
                    moneyAmount = MoneyAmount(
                        BigDecimal(Random.nextDouble(10.0, 1000.0)).setScale(
                            3,
                            RoundingMode.HALF_UP
                        )
                    ),
                    currency = MoneyCurrency(Currency.entries.toTypedArray().random())
                ),
                stock = Stock(Random.nextInt(0, 100))
            )
        }
    }

    private fun nullOrString(): String? {
        return if (Random.nextBoolean()) faker.fallout.quotes() else null
    }

    private fun nullOrName(): String? {
        return if (Random.nextBoolean()) faker.fallout.characters() else null
    }

    private fun nullOrLong(): Long? {
        return if (Random.nextBoolean()) Random.nextLong() else null
    }

    private fun nullOrTime(): Long? {
        return if (Random.nextBoolean()) Instant.now().plusSeconds(Random.nextLong(1, 100000)).toEpochMilli() else null
    }


    private fun generatePreferences(): Map<String, String> {
        val preferencesCount = Random.nextInt(1, 5)
        return (1..preferencesCount).associate {
            faker.harryPotter.spells() to faker.harryPotter.books()
        }
    }

    private fun nullOrPhone(): String? {
        return if (Random.nextBoolean()) faker.phoneNumber.cellPhone.number() else null
    }

    private fun nullOrAddress(): String? {
        return if (Random.nextBoolean()) faker.address.fullAddress() else null
    }

    private fun nullOrMoney(): Money? {
        return if (Random.nextBoolean()) {
            Money(
                moneyAmount = MoneyAmount(
                    BigDecimal(Random.nextDouble(10.0, 1000.0)).setScale(
                        3,
                        RoundingMode.HALF_UP
                    )
                ),
                currency = MoneyCurrency(Currency.entries.toTypedArray().random())
            )
        } else {
            null
        }
    }
}