package main.realtime_compression

import kotlinx.coroutines.runBlocking
import main.config.GeneratingProperties
import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import main.service.compression.CompressionService
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant


@Component
class DataCompressionRealTimeRunner(
    private val fileDataGenerationService: FileDataGenerationCompressionService,
    private val compressionService: CompressionService,
    private val generatingProperties: GeneratingProperties
) : CommandLineRunner {

    private val generatedDir = Path.of("generated_data")
    private val compressedDir = Path.of("compressed_data")

    private val userProfilesPath = generatedDir.resolve("user_profiles.json").toString()
    private val paymentsPath = generatedDir.resolve("payments.json").toString()
    private val ordersPath = generatedDir.resolve("orders.json").toString()

    override fun run(vararg args: String?) {
        println("Начало генерации и сжатия данных...")
        val timeStart = Instant.now().toEpochMilli()

        ensureDirectoriesExist()

        runBlocking {
            generateAndCompress(
                "Профили пользователей",
                { fileDataGenerationService.generateAndSaveUserProfilesToFile(generatingProperties.userProfiles.count, userProfilesPath) },
                userProfilesPath
            )
            generateAndCompress(
                "Платежи",
                { fileDataGenerationService.generateAndSavePaymentsToFile(generatingProperties.payments.count, paymentsPath) },
                paymentsPath
            )
            generateAndCompress(
                "Заказы",
                { fileDataGenerationService.generateAndSaveOrdersToFile(generatingProperties.orders.count, ordersPath) },
                ordersPath
            )
        }

        println("Генерация и сжатие данных завершены!")
        println("Общее время: ${(Instant.now().toEpochMilli() - timeStart)} мс")
    }

    private fun ensureDirectoriesExist() {
        if (!Files.exists(generatedDir)) {
            Files.createDirectories(generatedDir)
            println("Каталог $generatedDir создан.")
        }
        if (!Files.exists(compressedDir)) {
            Files.createDirectories(compressedDir)
            println("Каталог $compressedDir создан.")
        }
    }

    /**
     * Генерация данных и последующее сжатие.
     */
    private suspend fun generateAndCompress(
        entityName: String,
        generationAction: suspend () -> Unit,
        filePath: String
    ) {
        println("Генерация $entityName...")
        val generationStart = Instant.now().toEpochMilli()
        generationAction()
        println("$entityName сгенерированы за ${Instant.now().toEpochMilli() - generationStart} мс.")

        println("Сжатие $entityName...")
        val compressionStart = Instant.now().toEpochMilli()
        compressionService.compressFile(
            filePath = filePath,
            protocols = CompressionType.entries,
            outputDir = compressedDir
        )
        println("$entityName сжаты за ${Instant.now().toEpochMilli() - compressionStart} мс.")
    }
}