package main.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service

@Service
class JsonSerializationService {
    val objectMapper = jacksonObjectMapper()

    /**
     * Сериализация объекта в JSON-байты.
     */
    fun <T> serializeToBytes(entity: T): ByteArray {
        return objectMapper.writeValueAsBytes(entity)
    }

    /**
     * Десериализация JSON-байтов в объект.
     */
    fun <T> deserializeFromBytes(data: ByteArray, clazz: Class<T>): T {
        return objectMapper.readValue(data, clazz)
    }
}