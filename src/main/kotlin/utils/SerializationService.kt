package main.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.fasterxml.jackson.module.afterburner.AfterburnerModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Service
import java.io.InputStream
import java.io.OutputStream

@Service
class SerializationService {
    private val mapper =
        CBORMapper(CBORFactory())
            .registerModule(AfterburnerModule())
            .registerKotlinModule()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    /** Сериализация объекта в CBOR-байты */
    fun <T> serializeToBytes(entity: T): ByteArray =
        mapper.writeValueAsBytes(entity)

    /** Сериализация объекта напрямую в OutputStream */
    fun <T> serializeToStream(entity: T, output: OutputStream) {
        mapper.writeValue(output, entity)
    }

    /** Десериализация из CBOR-байтов */
    fun <T> deserializeFromBytes(data: ByteArray, clazz: Class<T>): T =
        mapper.readValue(data, clazz)

    /** Десериализация напрямую из InputStream */
    fun <T> deserializeFromStream(input: InputStream, clazz: Class<T>): T =
        mapper.readValue(input, clazz)
}
