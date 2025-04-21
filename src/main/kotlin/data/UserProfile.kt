package main.data

import main.utils.SerializationService
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.time.Instant


data class UserEmail(val value: String) {
    init {
        require(value.matches(Regex("^[A-Za-z0-9+_.'-]+@[A-Za-z0-9.-]+$"))) {
            "Invalid email address: $value"
        }
    }
}


enum class Gender {
    MALE,
    FEMALE,
    OTHER
}


/**
 * Модель данных для профиля пользователя.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val email: UserEmail,
    val phone: String? = null,
    val address: String? = null,
    val birthDate: Long? = null,
    val gender: Gender? = null,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val lastUpdatedAt: Long = Instant.now().toEpochMilli(),
    val preferences: Map<String, String> = emptyMap()
)

@WritingConverter
class UserProfileToStringConverter(
    private val json: SerializationService
) : Converter<UserProfile, String> {
    override fun convert(source: UserProfile): String =
        json.serializeToString(source)
}

@ReadingConverter
class StringToUserProfileConverter(
    private val json: SerializationService
) : Converter<String, UserProfile> {
    override fun convert(source: String): UserProfile =
        json.deserializeFromString(source, UserProfile::class.java)
}