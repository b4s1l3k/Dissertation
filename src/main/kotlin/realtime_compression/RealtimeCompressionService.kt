package main.realtime_compression

import main.service.compression.utils.CompressionProtocolFactory
import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Service
class RealtimeCompressionService(val compressionProtocolFactory: CompressionProtocolFactory) {

    fun compressFileWithSpecifiedProtocols(
        filePath: String,
        protocols: List<CompressionType>,
        outputDir: Path,
        targetFileName: String? = null
    ) {
        protocols.forEach { type ->
            val protocol = compressionProtocolFactory.getProtocol(type)

            val algorithmDir = outputDir.resolve(type.name.lowercase())
            if (!Files.exists(algorithmDir)) Files.createDirectories(algorithmDir)

            val finalFileName = targetFileName ?: File(filePath).name

            val compressedFilePath = algorithmDir.resolve("$finalFileName.${type.name.lowercase()}").toString()

            File(filePath).inputStream().use { inputStream ->
                File(compressedFilePath).outputStream().use { outputStream ->
                    protocol.compress(inputStream, outputStream)
                }
            }
        }
    }
}