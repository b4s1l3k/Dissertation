package main.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.afterburner.AfterburnerModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service

@Service
class SerializationService {
    private val objectMapper = jacksonObjectMapper()
        .registerModule(AfterburnerModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    /** Сериализация в JSON-байты */
    fun <T> serializeToBytes(entity: T): ByteArray =
        objectMapper.writeValueAsBytes(entity)

    /** Десериализация из JSON-байтов */
    fun <T> deserializeFromBytes(data: ByteArray, clazz: Class<T>): T =
        objectMapper.readValue(data, clazz)

    /** Сериализация в JSON-строку */
    fun <T> serializeToString(entity: T): String =
        objectMapper.writeValueAsString(entity)

    /** Десериализация из JSON-строки */
    fun <T> deserializeFromString(json: String, clazz: Class<T>): T =
        objectMapper.readValue(json, clazz)
}
