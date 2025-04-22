package main.benchmark

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import main.data.SimpleOrderInfo
import main.service.cassandra.CassandraService
import main.service.compression.SnappyCompressionProtocol
import main.service.generator.DataGenerationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.cassandra.core.cql.CqlTemplate
import org.springframework.stereotype.Service
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

@Service
class FallbackBenchmark(
    private val generator: DataGenerationService,
    @Qualifier("simpleCassandraService")
    private val simpleService: CassandraService<SimpleOrderInfo>,
    @Qualifier("cassandraCompressedService")
    private val cassandraCompressedService: CassandraService<SimpleOrderInfo>,
    @Qualifier("appCompressedService")
    private val appCompressedService: CassandraService<SimpleOrderInfo>,
    @Qualifier("appAndCassandraCompressedService")
    private val doubleCompressedService: CassandraService<SimpleOrderInfo>,
    private val cql: CqlTemplate,
    private val snappy: SnappyCompressionProtocol
) {
    private data class BurstResult(val timeMs: Long, val error: Boolean)

    private val Services = mapOf(
        "Simple" to simpleService,
        "CassCompressed" to cassandraCompressedService,
        "AppCompressed" to appCompressedService,
        "DoubleCompressed" to doubleCompressedService
    )

    fun runFallbackBenchmark(
        ordersCount: Int,
        batchSizes: List<Int> = listOf(50, 250, 500),
        readRatios: List<Double> = listOf(0.25, 0.5, 0.75),
        chunkSizes: List<Int> = listOf(16, 32, 64, 128, 256),
        blockSizes: List<Int> = listOf(8 * 1024, 32 * 1024, 64 * 1024, 128 * 1024),
        bursts: Int = 200,
        parallelBursts: Int = 10,
        warmUpBursts: Int = 10,
        warmUpDelayMs: Long = 100,
        interBurstDelayMs: Long = 1,
        errorThreshold: Double = 0.1,
        seed: Long = 42L
    ): Unit = runBlocking {
        println("=== Generating $ordersCount orders ===")
        val orders = generator.generateOrders(ordersCount)
            .mapIndexed { idx, o -> o.copy(id = "${o.id}_$idx") }

        // Цикл по Snappy.blockSize
        blockSizes.forEach { bs ->
            snappy.blockSize = bs
            println("\n>>> Snappy.blockSize = ${bs} B")
            preloadAll(Services, orders, parallelBursts)

            // Цикл по chunk_length_in_kb
            chunkSizes.forEach { chunkKb ->
                applyDeflateChunk(chunkKb)
                Services.forEach { (name, svc) ->
                    // Цикл по readRatio и batchSize
                    readRatios.forEach { ratio ->
                        batchSizes.forEach { batchSize ->
                            runScenario(
                                name, svc, orders,
                                batchSize, ratio,
                                bursts, parallelBursts,
                                warmUpBursts, warmUpDelayMs, interBurstDelayMs,
                                errorThreshold, seed, bs, chunkKb
                            )
                        }
                    }
                }
            }
        }

        println("\n=== All tests completed ===")
        exitProcess(0)
    }

    private suspend fun preloadAll(
        services: Map<String, CassandraService<SimpleOrderInfo>>,
        orders: List<SimpleOrderInfo>,
        parallelBursts: Int
    ) {
        println("Preloading data in $parallelBursts threads...")
        services.forEach { (name, svc) ->
            println("  → [$name]")
            svc.deleteAll()
            val chunkSize = (orders.size + parallelBursts - 1) / parallelBursts
            val sem = Semaphore(parallelBursts)
            coroutineScope {
                orders.chunked(chunkSize).map { batch ->
                    async(Dispatchers.IO) {
                        sem.acquire()
                        try {
                            svc.save(batch)
                        } finally {
                            sem.release()
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun applyDeflateChunk(chunkKb: Int) {
        println("\n=== ALTER TABLE set chunk_length_in_kb=$chunkKb ===")
        val q1 = """
            ALTER TABLE dissertation.cassandra_order_info
            WITH compression = {'class':'org.apache.cassandra.io.compress.DeflateCompressor',
                                'chunk_length_in_kb':'$chunkKb'}
        """.trimIndent()
        val q2 = q1.replace("cassandra_order_info", "app_compressed_order_info")
        cql.execute(q1)
        cql.execute(q2)
    }

    private suspend fun runScenario(
        name: String,
        svc: CassandraService<SimpleOrderInfo>,
        orders: List<SimpleOrderInfo>,
        batchSize: Int,
        readRatio: Double,
        bursts: Int,
        parallelBursts: Int,
        warmUpBursts: Int,
        warmUpDelayMs: Long,
        interBurstDelayMs: Long,
        errorThreshold: Double,
        seed: Long,
        blockSize: Int,
        chunkKb: Int
    ) {
        println(
            "\n--- Scenario: $name | block=${blockSize}B | chunk=${chunkKb}KiB | " +
                    "batch=$batchSize | readRatio=$readRatio ---"
        )

        // Warm‑up
        repeat(warmUpBursts) { i ->
            runMixedBurst(svc, orders, batchSize, readRatio, Random(seed + i))
            delay(warmUpDelayMs)
        }

        // Measured bursts
        val semBurst = Semaphore(parallelBursts)
        val results = mutableListOf<BurstResult>()
        coroutineScope {
            repeat(bursts) { i ->
                launch(Dispatchers.IO) {
                    semBurst.acquire()
                    try {
                        val rnd = Random(seed + warmUpBursts + i)
                        var error = false
                        val t = measureTimeMillis {
                            try {
                                runMixedBurst(svc, orders, batchSize, readRatio, rnd)
                            } catch (_: Exception) {
                                error = true
                            }
                            delay(interBurstDelayMs)
                        }
                        synchronized(results) {
                            results += BurstResult(t, error)
                        }
                    } finally {
                        semBurst.release()
                    }
                }
            }
        }

        printMetrics(results, bursts, errorThreshold)
    }

    private fun printMetrics(
        results: List<BurstResult>,
        bursts: Int,
        errorThreshold: Double
    ) {
        val times = results.map { it.timeMs }.sorted()
        val errors = results.count { it.error }
        val min = times.first()
        val max = times.last()
        val avg = times.average()
        val p95 = times[(bursts * 0.95).toInt().coerceAtMost(times.lastIndex)]
        val errRate = errors.toDouble() / bursts

        println("Completed $bursts bursts: errors=$errors (${String.format("%.1f%%", errRate * 100)})")
        println("Min=$min ms  Max=$max ms  Avg=${"%.1f".format(avg)} ms  P95=$p95 ms")

        if (errRate > errorThreshold) {
            println("❗ Error rate ${"%.1f%%".format(errRate * 100)} > threshold ${"%.1f%%".format(errorThreshold * 100)}, aborting.")
            throw CancellationException("Error rate too high")
        }
    }

    private suspend fun runMixedBurst(
        svc: CassandraService<SimpleOrderInfo>,
        orders: List<SimpleOrderInfo>,
        batchSize: Int,
        readRatio: Double,
        rnd: Random
    ) {
        val writes = (batchSize * (1 - readRatio)).toInt().coerceAtLeast(1)
        svc.save(List(writes) { orders.random(rnd) })
        repeat(batchSize - writes) {
            svc.findById(orders.random(rnd).id)
        }
    }
}
