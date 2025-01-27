package main.data

import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.Table
import java.io.Serializable
import java.time.Instant

@JvmInline
value class UserId(val value: String) : Serializable

@JvmInline
value class UserEmail(val value: String) {
    init {
        require(value.matches(Regex("^[A-Za-z0-9+_.'-]+@[A-Za-z0-9.-]+$"))) {
            "Invalid email address: $value"
        }
    }
}

@JvmInline
value class UserPhone(val value: String)

@JvmInline
value class UserAddress(val value: String)

enum class Gender {
    MALE,
    FEMALE,
    OTHER
}


/**
 * Модель данных для профиля пользователя.
 */
data class UserProfile(
    val id: UserId,
    val name: UserName,
    val email: UserEmail,
    val phone: UserPhone? = null,
    val address: UserAddress? = null,
    val birthDate: Long? = null,
    val gender: Gender? = null,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val lastUpdatedAt: Long = Instant.now().toEpochMilli(),
    val preferences: Map<String, String> = emptyMap()
)