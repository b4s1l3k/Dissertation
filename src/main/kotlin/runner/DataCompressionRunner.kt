package main.runner

import kotlinx.coroutines.runBlocking
import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import main.service.compression.CompressionService
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Service
class DataCompressionRunner(
    private val compressionService: CompressionService
) {

    private val generatedDir = Path.of("generated_data")
    private val compressedDir = Path.of("compressed_data")

    private val userProfilesPath = generatedDir.resolve("user_profiles.json").toString()
    private val paymentsPath = generatedDir.resolve("payments.json").toString()
    private val ordersPath = generatedDir.resolve("orders.json").toString()

    fun compressAllData() {
        println("Начало сжатия данных...")
        val timeStart = Instant.now().toEpochMilli()

        if (!Files.exists(compressedDir)) {
            Files.createDirectories(compressedDir)
            println("Папка $compressedDir создана.")
        }

        runBlocking {
            compressFile("user_profiles.json", userProfilesPath)
            compressFile("orders.json", ordersPath)
            compressFile("payments.json", paymentsPath)
        }

        println("Сжатие данных завершено!")
        println("Времени прошло: ${(Instant.now().toEpochMilli() - timeStart)} мс")
    }

    private suspend fun compressFile(fileName: String, filePath: String) {
        println("Сжимаем $fileName...")
        compressionService.compressFile(
            filePath = filePath,
            protocols = CompressionType.entries,
            outputDir = compressedDir
        )
    }
}