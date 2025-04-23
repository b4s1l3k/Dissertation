package main.benchmark

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import main.data.SimpleOrderInfo
import main.service.cassandra.CassandraService
import main.service.cassandra.utils.TableSizeReporter
import main.service.compression.SnappyCompressionProtocol
import main.service.generator.DataGenerationService
import main.utils.JvmMetricsService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.cassandra.core.cql.CqlTemplate
import org.springframework.stereotype.Service
import java.io.FileOutputStream
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
    private val snappy: SnappyCompressionProtocol,
    private val jvm: JvmMetricsService,
    private val sizeReporter: TableSizeReporter
) {

    private data class BurstResult(
        val timeMs: Long,
        val msPerOp: Double,
        val tps: Double,
        val cpu: Double,
        val memMiB: Double,
        val error: Boolean
    )

    private val services = mapOf(
        "Simple" to simpleService,
        "CassCompressed" to cassandraCompressedService,
        "AppCompressed" to appCompressedService,
        "DoubleCompressed" to doubleCompressedService
    )

    private val snappyStrategies = setOf("AppCompressed", "DoubleCompressed")
    private val deflateStrategies = setOf("CassCompressed", "DoubleCompressed")

    private val workbook = XSSFWorkbook()
    private val sheet = workbook.createSheet("Results")
    private var rowIdx = 0

    init {
        sheet.createRow(rowIdx++).apply {
            listOf(
                "strategy", "blockB", "chunkKiB", "batch", "readRatio",
                "ms/op_P95", "tps_P95", "cpu_P95", "mem_P95_MiB",
                "simple_KiB", "cassandra_KiB", "precomp_KiB", "appcomp_KiB",
                "errors", "errorRate"
            ).forEachIndexed { i, h -> createCell(i).setCellValue(h) }
        }
    }

    fun runFallbackBenchmark(
        ordersCount: Int,
        batchSizes: List<Int>,
        readRatios: List<Double>,
        chunkSizes: List<Int>,
        blockSizes: List<Int>,
        bursts: Int,
        parallelBursts: Int,
        warmUpBursts: Int,
        warmUpDelayMs: Long,
        interBurstDelay: Long,
        errThreshold: Double = 0.1,
        seed: Long = 42L
    ): Unit = runBlocking {

        println("=== Generating $ordersCount random orders ===")
        val orders = generator.generateOrders(ordersCount)
            .mapIndexed { i, o -> o.copy(id = "${o.id}_$i") }

        blockSizes.forEachIndexed { bsIdx, block ->
            snappy.blockSize = block
            println("\n>>> Snappy.blockSize = $block B")

            chunkSizes.forEachIndexed { ckIdx, chunkKb ->
                println(" → Deflate chunk_length_in_kb = $chunkKb KiB")

                alterDeflate(chunkKb)

                preload(orders, parallelBursts, services)

                val sizesKiB = sizeReporter.fetchTableSizes(
                    "simple_order_info",
                    "cassandra_order_info",
                    "precompressed_order_info",
                    "app_compressed_order_info"
                ).mapValues { it.value / 1024.0 }

                var sizeRecorded = false

                services.forEach { (strategy, svc) ->
                    val usesSnappy = strategy in snappyStrategies
                    val usesDeflate = strategy in deflateStrategies

                    if (!usesSnappy && bsIdx > 0) return@forEach
                    if (!usesDeflate && ckIdx > 0) return@forEach

                    val sizeRowArg: DoubleArray? =
                        if (!sizeRecorded) {
                            sizeRecorded = true
                            doubleArrayOf(
                                sizesKiB["simple_order_info"] ?: 0.0,
                                sizesKiB["cassandra_order_info"] ?: 0.0,
                                sizesKiB["precompressed_order_info"] ?: 0.0,
                                sizesKiB["app_compressed_order_info"] ?: 0.0
                            )
                        } else null

                    readRatios.forEach { rr ->
                        batchSizes.forEach { batch ->
                            runScenario(
                                strategy = strategy,
                                svc = svc,
                                orders = orders,
                                blockSize = block,
                                chunkKb = chunkKb,
                                batchSize = batch,
                                readRatio = rr,
                                bursts = bursts,
                                parallel = parallelBursts,
                                warmUpBursts = warmUpBursts,
                                warmUpDelayMs = warmUpDelayMs,
                                interDelayMs = interBurstDelay,
                                errThr = errThreshold,
                                seed = seed,
                                sizeRow = sizeRowArg
                            )
                        }
                    }
                }
            }
        }

        FileOutputStream("benchmark_results.xlsx").use { workbook.write(it) }
        workbook.close()
        println("\n✅  All benchmarks completed; results saved to benchmark_results.xlsx")
        exitProcess(0)
    }

    private suspend fun preload(
        orders: List<SimpleOrderInfo>,
        parallelism: Int,
        svcMap: Map<String, CassandraService<SimpleOrderInfo>>
    ) {
        println(" Preloading ${svcMap.size} tables…")
        svcMap.forEach { (name, svc) ->
            println("  → $name")
            svc.deleteAll()
            coroutineScope {
                orders.chunked((orders.size + parallelism - 1) / parallelism).map { chunk ->
                    launch(Dispatchers.IO) { svc.save(chunk) }
                }
            }
        }
    }

    private fun alterDeflate(chunkKb: Int) {
        cql.execute(
            "ALTER TABLE dissertation.cassandra_order_info " +
                    "WITH compression={'class':'org.apache.cassandra.io.compress.DeflateCompressor'," +
                    "'chunk_length_in_kb':$chunkKb}"
        )
        cql.execute(
            "ALTER TABLE dissertation.app_compressed_order_info " +
                    "WITH compression={'class':'org.apache.cassandra.io.compress.DeflateCompressor'," +
                    "'chunk_length_in_kb':$chunkKb}"
        )
    }

    private suspend fun runScenario(
        strategy: String,
        svc: CassandraService<SimpleOrderInfo>,
        orders: List<SimpleOrderInfo>,
        blockSize: Int,
        chunkKb: Int,
        batchSize: Int,
        readRatio: Double,
        bursts: Int,
        parallel: Int,
        warmUpBursts: Int,
        warmUpDelayMs: Long,
        interDelayMs: Long,
        errThr: Double,
        seed: Long,
        sizeRow: DoubleArray?
    ) {
        println("--- $strategy | block=$blockSize B | chunk=$chunkKb KiB | batch=$batchSize | read=$readRatio ---")

        repeat(warmUpBursts) { i ->
            runBurst(svc, orders, batchSize, readRatio, Random(seed + i))
            delay(warmUpDelayMs)
        }

        val sem = Semaphore(parallel)
        val stats = mutableListOf<BurstResult>()

        coroutineScope {
            repeat(bursts) { i ->
                launch(Dispatchers.IO) {
                    sem.acquire()
                    val rnd = Random(seed + warmUpBursts + i)
                    val elapsed = measureTimeMillis {
                        runBurst(svc, orders, batchSize, readRatio, rnd)
                    }
                    val m = jvm.sample()
                    stats += BurstResult(
                        timeMs = elapsed,
                        msPerOp = elapsed.toDouble() / batchSize,
                        tps = if (elapsed > 0) batchSize * 1000.0 / elapsed else Double.NaN,
                        cpu = m.cpuPct,
                        memMiB = m.totalUsedMiB,
                        error = false
                    )
                    sem.release()
                    delay(interDelayMs)
                }
            }
        }

        persistMetrics(
            strategy, blockSize, chunkKb, batchSize, readRatio,
            stats, bursts, errThr, sizeRow
        )
    }

    private suspend fun runBurst(
        svc: CassandraService<SimpleOrderInfo>,
        orders: List<SimpleOrderInfo>,
        batchSize: Int,
        readRatio: Double,
        rnd: Random
    ) {
        val writes = (batchSize * (1 - readRatio)).toInt().coerceAtLeast(1)
        svc.save(List(writes) { orders.random(rnd) })
        repeat(batchSize - writes) { svc.findById(orders.random(rnd).id) }
    }

    private fun persistMetrics(
        strategy: String,
        block: Int,
        chunkKb: Int,
        batch: Int,
        readRatio: Double,
        results: List<BurstResult>,
        bursts: Int,
        errThr: Double,
        sizeRow: DoubleArray?
    ) {
        fun List<Double>.p95() = this[(size * 0.95).toInt().coerceAtMost(lastIndex)]

        val perOp = results.map { it.msPerOp }.sorted().p95()
        val tps = results.map { it.tps }.sorted().p95()
        val cpu = results.map { it.cpu }.sorted().p95()
        val mem = results.map { it.memMiB }.sorted().p95()
        val errCnt = results.count { it.error }
        val errRate = errCnt.toDouble() / bursts

        sheet.createRow(rowIdx++).apply {
            createCell(0).setCellValue(strategy)
            createCell(1).setCellValue(block.toDouble())
            createCell(2).setCellValue(chunkKb.toDouble())
            createCell(3).setCellValue(batch.toDouble())
            createCell(4).setCellValue(readRatio)
            createCell(5).setCellValue(perOp)
            createCell(6).setCellValue(tps)
            createCell(7).setCellValue(cpu)
            createCell(8).setCellValue(mem)

            if (sizeRow != null) {
                createCell(9).setCellValue(sizeRow[0])
                createCell(10).setCellValue(sizeRow[1])
                createCell(11).setCellValue(sizeRow[2])
                createCell(12).setCellValue(sizeRow[3])
            } else {
                (9..12).forEach { idx -> createCell(idx) }
            }

            createCell(13).setCellValue(errCnt.toDouble())
            createCell(14).setCellValue(errRate)
        }
    }
}
