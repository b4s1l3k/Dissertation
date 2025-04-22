package main.data

import org.springframework.data.cassandra.core.mapping.UserDefinedType
import java.io.Serializable


enum class Gender {
    MALE,
    FEMALE,
    OTHER
}

@UserDefinedType("user_profile_type")
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val address: String?,
    val birthDate: Long?,
    val gender: String?,
    val createdAt: Long,
    val lastUpdatedAt: Long,
    val preferences: Map<String, String>
) : Serializable