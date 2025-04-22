package main.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.fasterxml.jackson.module.afterburner.AfterburnerModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Service

@Service
class SerializationService {
    private val mapper = CBORMapper(CBORFactory())
        .registerModule(AfterburnerModule())
        .registerKotlinModule()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    /** Сериализация в CBOR-байты */
    fun <T> serializeToBytes(entity: T): ByteArray =
        mapper.writeValueAsBytes(entity)

    /** Десериализация из CBOR-байт */
    fun <T> deserializeFromBytes(data: ByteArray, clazz: Class<T>): T =
        mapper.readValue(data, clazz)
}
