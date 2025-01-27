package main

import main.config.ApplicationProperties
import main.config.ApplicationTypes
//import main.runner.CassandraDataGenerationAndCompressionRunner
import main.runner.DataCompressionRunner
import main.runner.DataGenerationRunner
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Order(1)
@Component
class Application(
    private val applicationProperties: ApplicationProperties,
    private val dataCompressionRunner: DataCompressionRunner,
    private val dataGenerationRunner: DataGenerationRunner,
//    private val cassandraDataGenerationAndCompressionRunner: CassandraDataGenerationAndCompressionRunner
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        when (applicationProperties.applicationType.type) {
            ApplicationTypes.cassandra -> {
                println("Режим: запись данных в Cassandra")
//                cassandraDataGenerationAndCompressionRunner.generateAndProcessDataForAllCompressionTypes()
            }
            ApplicationTypes.generating -> {
                println("Режим: генерация данных без сжатия")
                dataGenerationRunner.generateAllData()
            }
            ApplicationTypes.compression -> {
                println("Режим: только сжатие данных")
                dataCompressionRunner.compressAllData()
            }
            ApplicationTypes.generatingPlusCompression -> {
                println("Режим: генерация данных и их сжатие")
                dataGenerationRunner.generateAllData()
                dataCompressionRunner.compressAllData()
            }
        }
    }
}