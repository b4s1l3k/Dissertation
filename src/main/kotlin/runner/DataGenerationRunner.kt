package main.runner

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import main.config.GeneratingProperties
import main.service.generator.DataGenerationService
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Service
class DataGenerationRunner(
    private val dataGenerationService: DataGenerationService,
    private val generatingProperties: GeneratingProperties
) {

    private val generatedDir = Path.of("generated_data")

    private val userProfilesPath = generatedDir.resolve("user_profiles.json").toString()
    private val paymentsPath = generatedDir.resolve("payments.json").toString()
    private val ordersPath = generatedDir.resolve("orders.json").toString()

    fun generateAllData() {
        println("Начало генерации данных...")
        val timeStart = Instant.now().toEpochMilli()

        if (!Files.exists(generatedDir)) {
            Files.createDirectories(generatedDir)
            println("Папка $generatedDir создана.")
        }

        runBlocking {
            val userProfilesJob = async {
                dataGenerationService.generateAndSaveUserProfilesToFile(
                    count = generatingProperties.userProfiles.count,
                    filePath = userProfilesPath
                )
            }

            val paymentsJob = async {
                dataGenerationService.generateAndSavePaymentsToFile(
                    count = generatingProperties.payments.count,
                    filePath = paymentsPath
                )
            }

            val ordersJob = async {
                dataGenerationService.generateAndSaveOrdersToFile(
                    count = generatingProperties.orders.count,
                    filePath = ordersPath
                )
            }

            userProfilesJob.await()
            paymentsJob.await()
            ordersJob.await()
        }

        println("Генерация данных завершена!")
        println("Времени прошло: ${(Instant.now().toEpochMilli() - timeStart)} мс")
    }
}