package main.runner

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import main.config.GeneratingProperties
import main.data.OrderInfo
import main.service.cassandra.CompressedCassandraService
import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import main.service.generator.utils.DataClassGenerator
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class CassandraDataGenerationAndCompressionRunner(
    private val dataClassGenerator: DataClassGenerator,
    private val compressedCassandraService: CompressedCassandraService,
    private val generatingProperties: GeneratingProperties
) {

    fun generateAndProcessDataForAllCompressionTypes() {
        println("Начало генерации, сжатия и отправки данных в Cassandra...")
        val timeStart = Instant.now().toEpochMilli()

        runBlocking {
            val compressionJobs = CompressionType.entries.map { compressionType ->
                async {
                    processDataForCompressionType(compressionType)
                }
            }

            compressionJobs.forEach { it.await() }
        }

        println("Обработка данных завершена!")
        println("Времени прошло: ${(Instant.now().toEpochMilli() - timeStart)} мс")
    }

    private suspend fun processDataForCompressionType(compressionType: CompressionType) {
        val count = generatingProperties.orders.count
        println("Обработка данных с использованием сжатия: ${compressionType.name}...")

        repeat(count) { index ->
            val order = dataClassGenerator.generateOrderInfo()
            compressedCassandraService.saveCompressed(order, compressionType)
            if (index % 100 == 0) {
                println("Сохранено $index записей с использованием ${compressionType.name}.")
            }
        }

        println("Обработка с использованием ${compressionType.name} завершена.")
    }
}