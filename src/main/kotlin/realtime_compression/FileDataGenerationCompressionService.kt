package main.realtime_compression

import main.data.OrderInfo
import main.data.Payment
import main.data.UserProfile
import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import main.service.generator.utils.DataClassGenerator
import main.utils.JsonFileWriterService
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

interface FileDataGenerationCompressionService {
    suspend fun generateAndSaveUserProfilesToFile(count: Int, filePath: String)
    suspend fun generateAndSavePaymentsToFile(count: Int, filePath: String)
    suspend fun generateAndSaveOrdersToFile(count: Int, filePath: String)
}

@Service
class FileDataGenerationCompressionServiceImpl(
    private val dataGenerator: DataClassGenerator,
    private val userProfileWriterService: JsonFileWriterService<UserProfile>,
    private val paymentWriterService: JsonFileWriterService<Payment>,
    private val orderInfoWriterService: JsonFileWriterService<OrderInfo>,
    private val realtimeCompressionService: RealtimeCompressionService
) : FileDataGenerationCompressionService {
    /**
     * Асинхронная генерация и сохранение профилей пользователей.
     */
    override suspend fun generateAndSaveUserProfilesToFile(count: Int, filePath: String) {
        generateAndCompressEach(
            count,
            filePath,
            "compressed_user_profiles",
            dataGenerator::generateUserProfile,
            userProfileWriterService
        )
    }

    /**
     * Асинхронная генерация и сохранение платежей.
     */
    override suspend fun generateAndSavePaymentsToFile(count: Int, filePath: String) {
        generateAndCompressEach(
            count,
            filePath,
            "compressed_payments",
            dataGenerator::generatePayment,
            paymentWriterService
        )
    }

    /**
     * Асинхронная генерация и сохранение заказов.
     */
    override suspend fun generateAndSaveOrdersToFile(count: Int, filePath: String) {
        generateAndCompressEach(
            count,
            filePath,
            "compressed_orders",
            dataGenerator::generateOrderInfo,
            orderInfoWriterService
        )
    }

    /**
     * Универсальный метод генерации, сохранения и сжатия каждого объекта.
     */
    private suspend fun <T> generateAndCompressEach(
        count: Int,
        originalFilePath: String,
        compressedFilePrefix: String,
        generator: () -> T,
        writerService: JsonFileWriterService<T>
    ) {
        val originalFile = File(originalFilePath)
        val compressedDir = Path.of("compressed_data")
        if (!Files.exists(compressedDir)) Files.createDirectories(compressedDir)

        originalFile.outputStream().use { outputStream ->
            val generatorStream = writerService.objectMapper.factory.createGenerator(outputStream)
            generatorStream.writeStartArray()

            repeat(count) { index ->
                val item = generator()
                generatorStream.writeObject(item)

                val compressedFileBaseName = "$compressedFilePrefix-$index.json"

                val tempFile = File.createTempFile("temp", ".json")
                tempFile.writeText(writerService.objectMapper.writeValueAsString(item))

                realtimeCompressionService.compressFileWithSpecifiedProtocols(
                    filePath = tempFile.absolutePath,
                    protocols = listOf(CompressionType.LZ4, CompressionType.SNAPPY, CompressionType.ZSTD),
                    outputDir = compressedDir,
                    targetFileName = compressedFileBaseName
                )

                tempFile.delete()
            }

            generatorStream.writeEndArray()
            generatorStream.close()
        }
    }
}