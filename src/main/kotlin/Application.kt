package main

import main.benchmark.CassandraFindAllBenchmark
import main.benchmark.FallbackBenchmark
import main.benchmark.ReadBenchmark
import main.config.*
import main.runner.CassandraCompressedRunner
import main.runner.PreCompressedRunner
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class Application(
    private val appProps: ApplicationProperties,
    private val benchProps: BenchmarkProperties,
    private val preCompressedRunner: PreCompressedRunner,
    private val cassandraCompressedRunner: CassandraCompressedRunner,
    private val pageSizeBenchmark: CassandraFindAllBenchmark,
    private val readBenchmark: ReadBenchmark,
    private val fallbackBenchmark: FallbackBenchmark,
    private val generatingProps: GeneratingProperties
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
                        readRatios = benchProps.readRatios,
                        chunkSizes = benchProps.chunkSizes,
                        blockSizes = benchProps.blockSizes,
                        bursts = benchProps.bursts,
                        parallelBursts = benchProps.parallelBursts,
                        warmUpBursts = benchProps.warmUpBursts,
                        warmUpDelayMs = benchProps.warmUpDelayMs,
                        interBurstDelay = benchProps.interBurstDelayMs
                    )

                BenchmarkType.parallelism ->
                    println("Benchmark 'parallelism' пока не реализован")
            }

            else ->
                println("Тип приложения ${appProps.type} пока не реализован")
        }
    }
}
