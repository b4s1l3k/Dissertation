package main

import main.benchmark.CassandraFindAllBenchmark
import main.benchmark.FallbackBenchmark
import main.benchmark.ReadBenchmark
//import main.benchmark.SizeBenchmark
import main.config.*
import main.runner.CassandraCompressedRunner
import main.runner.PreCompressedRunner
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component

@SpringBootApplication
@EnableScheduling
class MainApplication

@Component
class MainRunner(
    private val appProps: ApplicationProperties,
    private val benchProps: BenchmarkProperties,
    private val generatingProps: GeneratingProperties,
    private val preCompressedRunner: PreCompressedRunner,
    private val cassandraCompressedRunner: CassandraCompressedRunner,
    private val pageSizeBenchmark: CassandraFindAllBenchmark,
    private val readBenchmark: ReadBenchmark,
    private val fallbackBenchmark: FallbackBenchmark,
//    private val sizeBenchmark: SizeBenchmark
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        when (appProps.type) {
            ApplicationTypes.preCompressed ->
                preCompressedRunner.preCompresedRun(
                    appProps.count,
                    generatingProps.perCall,
                    appProps.randomCount
                )

            ApplicationTypes.cassandraCompressed ->
                cassandraCompressedRunner.cassandraCompressedRun(
                    appProps.count,
                    generatingProps.perCall,
                    appProps.randomCount
                )

            ApplicationTypes.benchmark -> when (appProps.benchmarkType) {
                BenchmarkType.pageSize ->
                    pageSizeBenchmark.runBenchmark(
                        ordersCount = appProps.count,
                        perCall = generatingProps.perCall,
                        randomCount = appProps.randomCount
                    )

                BenchmarkType.read ->
                    readBenchmark.runBenchmark(
                        ordersCount = appProps.count
                    )

                BenchmarkType.fallback ->
                    fallbackBenchmark.runFallbackBenchmark(
                        ordersCount = benchProps.ordersCount,
                        batchSizes = benchProps.batchSizes,
                        chunkSizes = benchProps.chunkSizes,
                        blockSizes = benchProps.blockSizes,
                        parallelism = benchProps.parallelBursts,
                        interDelayMs = benchProps.interBurstDelayMs
                    )
//
//                BenchmarkType.size ->
//                    sizeBenchmark.runSizeBenchmark(
//                        ordersCount = benchProps.ordersCount,
//                        blockSizes = benchProps.blockSizes,
//                        chunkSizes = benchProps.chunkSizes
//                    )

                else ->
                    println("Benchmark ${appProps.benchmarkType} пока не реализован")
            }

            else ->
                println("Тип приложения ${appProps.type} пока не реализован")
        }
    }
}

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
}