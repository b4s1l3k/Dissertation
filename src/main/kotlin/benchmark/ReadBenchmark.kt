package main.benchmark

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import main.data.SimpleOrderInfo
import main.service.cassandra.CassandraService
import main.service.generator.DataGenerationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import kotlin.random.Random
import kotlin.system.measureTimeMillis

@Service
class ReadBenchmark(
    private val generator: DataGenerationService,

    @Qualifier("simpleCassandraService")
    private val simpleService: CassandraService<SimpleOrderInfo>,

    @Qualifier("cassandraCompressedService")
    private val cassandraCompressedService: CassandraService<SimpleOrderInfo>,

    @Qualifier("appCompressedService")
    private val appCompressedService: CassandraService<SimpleOrderInfo>,

    @Qualifier("appAndCassandraCompressedService")
    private val doubleCompressedService: CassandraService<SimpleOrderInfo>
) {

    private data class BenchmarkResult(
        val strategy: String,
        val scenario: String,
        val params: String,
        val timeMs: Long,
        val errors: Int = 0
    )

    fun runBenchmark(ordersCount: Int) = runBlocking {
        val pageSizes = listOf(100, 500, 1000)
        val randomReadCounts = listOf(1_000, 5_000, 10_000)
        val parallelisms = listOf(2, 4, 10)
        val mixedDurations = listOf(1, 2).map { it.toLong() }
        val readsPerWrites = listOf(50, 150, 300)
        val fallbackBatches = listOf(50, 250, 500)
        val fallbackBursts = 100

        println("Generating $ordersCount orders...")
        val seed = 12345L
        val orders = generator.generateOrders(ordersCount)
            .mapIndexed { idx, o -> o.copy(id = "${o.id}_$idx") }
        val maxReads = randomReadCounts.maxOrNull() ?: 0
        val allIds = orders.map { it.id }
            .shuffled(Random(seed))
            .take(maxReads)

        val services = mapOf(
            "Simple" to simpleService,
            "CassCompressed" to cassandraCompressedService,
            "AppCompressed" to appCompressedService,
            "DoubleCompressed" to doubleCompressedService
        )
        services.forEach { (label, svc) ->
            println("Preloading [$label] (${orders.size} records)...")
            svc.deleteAll()
            svc.save(orders)
        }

        suspend fun fullScan(svc: CassandraService<SimpleOrderInfo>, pageSize: Int) {
            svc.findAll(pageSize)
        }

        suspend fun randomReads(
            svc: CassandraService<SimpleOrderInfo>,
            ids: List<String>,
            parallelism: Int
        ) = coroutineScope {
            val sem = Semaphore(parallelism)
            ids.map { id ->
                async(Dispatchers.IO) {
                    sem.acquire()
                    try {
                        svc.findById(id)
                    } finally {
                        sem.release()
                    }
                }
            }.awaitAll()
        }

        suspend fun mixedWorkload(
            svc: CassandraService<SimpleOrderInfo>,
            orders: List<SimpleOrderInfo>,
            readsPerWrite: Int,
            durationSec: Long
        ) {
            coroutineScope {
                val reader = launch(Dispatchers.IO) {
                    repeat(readsPerWrite * durationSec.toInt()) {
                        svc.findById(orders.random().id)
                    }
                }
                val writer = launch(Dispatchers.IO) {
                    repeat(durationSec.toInt()) {
                        delay(1_000)
                        svc.save(listOf(orders.random().copy(status = orders.first().status)))
                    }
                }
                delay(durationSec * 1_000)
                reader.cancelAndJoin()
                writer.cancelAndJoin()
            }
        }

        val results = mutableListOf<BenchmarkResult>()

        for (ps in pageSizes) {
            services.forEach { (label, svc) ->
                print("Running fullScan(pageSize=$ps) on $label... ")
                var errors = 0
                val t = measureTimeMillis {
                    try {
                        fullScan(svc, ps)
                    } catch (e: Exception) {
                        errors = 1
                    }
                }
                println("${t} ms")
                results += BenchmarkResult(label, "fullScan", "pageSize=$ps", t, errors)
            }
        }

        for (rc in randomReadCounts) {
            val ids = allIds.take(rc)
            for (par in parallelisms) {
                services.forEach { (label, svc) ->
                    print("Running randomReads(count=$rc,par=$par) on $label... ")
                    var errors = 0
                    val t = measureTimeMillis {
                        try {
                            randomReads(svc, ids, par)
                        } catch (e: Exception) {
                            errors = 1
                        }
                    }
                    println("$t ms")
                    results += BenchmarkResult(label, "randomReads", "count=$rc,par=$par", t, errors)
                }
            }
        }

        for (dur in mixedDurations) {
            for (rpw in readsPerWrites) {
                services.forEach { (label, svc) ->
                    print("Running mixedWorkload(${dur}s,rpw=$rpw) on $label... ")
                    var errors = 0
                    val t = measureTimeMillis {
                        try {
                            mixedWorkload(svc, orders, rpw, dur)
                        } catch (e: Exception) {
                            errors = 1
                        }
                    }
                    println("$t ms")
                    results += BenchmarkResult(label, "mixedWorkload", "dur=${dur}s,rpw=$rpw", t, errors)
                }
            }
        }

        for (batchSize in fallbackBatches) {
            services.forEach { (label, svc) ->
                print("Running fallback(batch=$batchSize,bursts=$fallbackBursts) on $label... ")
                var errors = 0
                val t = measureTimeMillis {
                    repeat(fallbackBursts) { burstIdx ->
                        val batch = List(batchSize) { orders.random(Random(seed + burstIdx)) }
                        try {
                            svc.save(batch)
                            batch.forEach { svc.findById(it.id) }
                        } catch (e: Exception) {
                            errors++
                        }
                    }
                }
                println("$t ms, errors=$errors")
                results += BenchmarkResult(label, "fallback", "batch=$batchSize,bursts=$fallbackBursts", t, errors)
            }
        }

        println("\n| Strategy        | Scenario        | Params                      | Time (ms) | Errors |")
        println("|-----------------|-----------------|-----------------------------|-----------|--------|")
        results.forEach { r ->
            println(
                "| ${r.strategy.padEnd(15)} | ${r.scenario.padEnd(15)} | ${r.params.padEnd(27)} | ${
                    r.timeMs.toString().padEnd(9)
                } | ${r.errors.toString().padEnd(6)} |"
            )
        }

        println("\n=== Combined benchmark completed ===")
    }
}
