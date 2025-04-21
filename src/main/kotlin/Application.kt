package main

import main.benchmark.CassandraFindAllBenchmark
import main.config.ApplicationProperties
import main.config.ApplicationTypes
import main.config.BenchmarkType
import main.config.GeneratingProperties
import main.runner.CassandraCompressedRunner
import main.runner.PreCompressedRunner
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class Application(
    private val props: ApplicationProperties,
    private val preCompressedRunner: PreCompressedRunner,
    private val cassandraCompressedRunner: CassandraCompressedRunner,
    private val pageSizeBenchmark: CassandraFindAllBenchmark,
    private val generatingProps: GeneratingProperties
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        when (props.type) {
            ApplicationTypes.preCompressed ->
                preCompressedRunner.preCompresedRun(
                    props.count,
                    generatingProps.perCall,
                    props.randomCount
                )

            ApplicationTypes.cassandraCompressed ->
                cassandraCompressedRunner.cassandraCompressedRun(
                    props.count,
                    generatingProps.perCall,
                    props.randomCount
                )

            ApplicationTypes.benchmark -> when (props.benchmarkType) {
                BenchmarkType.pageSize ->
                    pageSizeBenchmark.runBenchmark(
                        ordersCount = props.count,
                        perCall     = generatingProps.perCall,
                        randomCount = props.randomCount
                    )

                BenchmarkType.parallelism ->
                    println("Benchmark 'parallelism' пока не реализован")
            }

            else ->
                println("Тип приложения ${props.type} пока не реализован")
        }
    }
}
