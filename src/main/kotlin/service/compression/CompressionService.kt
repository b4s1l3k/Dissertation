package main.service.compression

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import main.service.compression.utils.CompressionProtocol
import main.service.compression.utils.CompressionProtocolFactory
import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.time.Instant


interface CompressionService {
    fun compressFile(filePath: String, protocols: List<CompressionType>, outputDir: Path)
    fun compressData(data: ByteArray, protocol: CompressionType): ByteArray
    fun decompressData(data: ByteArray, protocol: CompressionType): ByteArray
}

@Service
class CompressionServiceImpl(val compressionProtocolFactory: CompressionProtocolFactory) : CompressionService{

    private val objectMapper = jacksonObjectMapper()

    /**
     * Сжатие файла с использованием указанных протоколов.
     */
    override fun compressFile(filePath: String, protocols: List<CompressionType>, outputDir: Path) {
        protocols.forEach { type ->
            val protocol = compressionProtocol(type)
            val fileName = File(filePath).name
            val compressedFilePath = outputDir.resolve("$fileName.${type.name.lowercase()}").toString()
            println("Сжимаем файл $filePath в $compressedFilePath с использованием ${type.name}...")
            val startTime = Instant.now().toEpochMilli()

            File(filePath).inputStream().use { inputStream ->
                File(compressedFilePath).outputStream().use { outputStream ->
                    protocol.compress(inputStream, outputStream)
                }
            }
            println(
                "Времени с использованием ${type.name} для сжатия $filePath потрачено: ${
                    Instant.now().toEpochMilli() - startTime
                }"
            )
        }
    }

    override fun compressData(data: ByteArray, protocol: CompressionType): ByteArray {
        val compressionProtocol = compressionProtocolFactory.getProtocol(protocol)
        val outputStream = ByteArrayOutputStream()

        ByteArrayInputStream(data).use { inputStream ->
            compressionProtocol.compress(inputStream, outputStream)
        }

        return outputStream.toByteArray()
    }

    override fun decompressData(data: ByteArray, protocol: CompressionType): ByteArray {
        val compressionProtocol = compressionProtocolFactory.getProtocol(protocol)
        val outputStream = ByteArrayOutputStream()

        ByteArrayInputStream(data).use { inputStream ->
            compressionProtocol.decompress(inputStream, outputStream)
        }

        return outputStream.toByteArray()
    }

    private fun compressionProtocol(type: CompressionType): CompressionProtocol {
        return compressionProtocolFactory.getProtocol(type)
    }
}