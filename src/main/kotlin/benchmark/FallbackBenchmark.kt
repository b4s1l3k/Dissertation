package main.benchmark

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import main.benchmark.utils.BenchmarkReportService
import main.data.SimpleOrderInfo
import main.service.cassandra.CassandraService
import main.service.cassandra.utils.TableSizeReporter
import main.service.compression.SnappyCompressionProtocol
import main.service.generator.DataGenerationService
import main.utils.JvmMetricsService
import main.utils.Retry.retry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.cassandra.core.cql.CqlTemplate
import org.springframework.stereotype.Service
import java.util.*
import kotlin.system.exitProcess
import kotlin.system.measureNanoTime

private const val SimpleStrategy = "Simple"
private const val CassCompressedStrategy = "CassCompressed"
private const val AppCompressedStrategy = "AppCompressed"
private const val DoubleCompressedStrategy = "DoubleCompressed"

private const val SimpleTable = "simple_order_info"
private const val CassandraCompressedTable = "cassandra_order_info"
private const val AppCompressedTable = "app_compressed_order_info"
private const val PrecompressedTable = "precompressed_order_info"

private val StrategyToTable = mapOf(
    SimpleStrategy to SimpleTable,
    CassCompressedStrategy to CassandraCompressedTable,
    AppCompressedStrategy to AppCompressedTable,
    DoubleCompressedStrategy to PrecompressedTable
)

@Service
class FallbackBenchmark(
    private val generator: DataGenerationService,

    @Qualifier("simpleCassandraService")
    private val simpleSvc: CassandraService<SimpleOrderInfo>,
    @Qualifier("cassandraCompressedService")
    private val cassSvc: CassandraService<SimpleOrderInfo>,
    @Qualifier("appCompressedService")
    private val appSvc: CassandraService<SimpleOrderInfo>,
    @Qualifier("appAndCassandraCompressedService")
    private val precompSvc: CassandraService<SimpleOrderInfo>,

    private val cql: CqlTemplate,
    private val snappy: SnappyCompressionProtocol,
    private val jvm: JvmMetricsService,
    private val sizeRp: TableSizeReporter,
    private val report: BenchmarkReportService
) {
    private data class Phase(val msOpP95: Double, val tpsP95: Double, val cpuP95: Double)

    private val services = mapOf(
        SimpleStrategy to simpleSvc,
        CassCompressedStrategy to cassSvc,
        AppCompressedStrategy to appSvc,
        DoubleCompressedStrategy to precompSvc
    )

    fun runFallbackBenchmark(
        ordersCount : Int,
        batchSizes  : List<Int>,
        chunkSizes  : List<Int>,
        blockSizes  : List<Int>,
        parallelism : Int,
        interDelayMs: Long
    ) {
        val handler = CoroutineExceptionHandler { _, e ->
            println("⛔ Unhandled error: ${e.message ?: e}")
            e.printStackTrace()
        }

        runBlocking(handler) {
            try {
                println("=== Generating $ordersCount random orders ===")
                val orders = generator.generateOrders(ordersCount)
                    .mapIndexed { i, o -> o.copy(id = "${o.id}_$i") }

                for ((bsIdx, blockB) in blockSizes.withIndex()) {
                    snappy.blockSize = blockB
                    println("\n>>> Snappy.blockSize = $blockB B")

                    for ((ckIdx, chunkKb) in chunkSizes.withIndex()) {
                        println("\n → Deflate chunk_length_in_kb = $chunkKb KiB")
                        alterDeflate(chunkKb)

                        val toTest = services.filter { (str, _) ->
                            when (str) {
                                SimpleStrategy          -> bsIdx == 0 && ckIdx == 0
                                CassCompressedStrategy  -> bsIdx == 0
                                AppCompressedStrategy   -> ckIdx == 0
                                DoubleCompressedStrategy-> true
                                else                    -> false
                            }
                        }

                        for (batch in batchSizes) {
                            for ((strategy, svc) in toTest) {
                                println("--- $strategy | block=$blockB B | chunk=$chunkKb KiB | batch=$batch ---")

                                runCatching {
                                    truncateAll()
                                    preloadAll(orders, batch, svc, parallelism)

                                    val (w, r) = measureRW(
                                        orders, batch, svc, parallelism, interDelayMs
                                    )

                                    val tbl = StrategyToTable.getValue(strategy)
                                    sizeRowFor(tbl, blockB, chunkKb, batch)
                                    persist(strategy, blockB, chunkKb, batch, w, r)

                                    truncateAll()
                                    sizeRp.clearSnapshots()
                                }.onFailure { ex ->
                                    println("⚠️  Batch failed: ${ex.message ?: ex}")
                                    ex.printStackTrace()
                                }
                            }
                        }
                    }
                }

                println("✔ Benchmarks finished")
            } finally {
                runCatching { report.saveTo() }

                exitProcess(0)
            }
        }
    }

    private suspend fun measureRW(
        orders: List<SimpleOrderInfo>,
        batchSize: Int,
        svc: CassandraService<SimpleOrderInfo>,
        parallel: Int,
        interDelayMs: Long
    ): Pair<Phase, Phase> {

        fun MutableList<Long>.p95ns(): Long =
            if (isEmpty()) 0 else sorted()[(size * 0.95).toInt().coerceAtMost(lastIndex)]

        fun MutableList<Double>.p95(): Double =
            if (isEmpty()) Double.NaN else sorted()[(size * 0.95).toInt().coerceAtMost(lastIndex)]

        val wTimes = Collections.synchronizedList(mutableListOf<Long>())
        val wCpu = Collections.synchronizedList(mutableListOf<Double>())

        coroutineScope {
            val sem = Semaphore(parallel)
            for (slice in orders.chunked(batchSize)) launch(Dispatchers.IO) {
                sem.acquire()
                try {
                    val cpu0 = jvm.snap()
                    val ns = measureNanoTime { retry { svc.save(slice) } }
                    wTimes += ns / batchSize
                    wCpu += jvm.cpuLoadPct(cpu0, jvm.snap())
                } finally {
                    sem.release()
                }
            }
        }

        delay(interDelayMs)

        val rTimes = Collections.synchronizedList(mutableListOf<Long>())
        val rCpu = Collections.synchronizedList(mutableListOf<Double>())

        coroutineScope {
            val sem = Semaphore(parallel)
            for (ids in orders.chunked(batchSize).map { it.map(SimpleOrderInfo::id) })
                launch(Dispatchers.IO) {
                    sem.acquire()
                    try {
                        val cpu0 = jvm.snap()
                        val ns = measureNanoTime { retry { svc.findByIds(ids) } }
                        rTimes += ns / batchSize
                        rCpu += jvm.cpuLoadPct(cpu0, jvm.snap())
                    } finally {
                        sem.release()
                    }
                }
        }

        fun phase(nsList: MutableList<Long>, cpuList: MutableList<Double>): Phase {
            val op95µs = nsList.p95ns() / 1_000.0
            val tps95 = if (op95µs > 0) 1_000_000.0 / op95µs else 0.0
            return Phase(op95µs, tps95, cpuList.p95())
        }
        return phase(wTimes, wCpu) to phase(rTimes, rCpu)
    }

    private suspend fun preloadAll(
        orders: List<SimpleOrderInfo>,
        batch: Int,
        svc: CassandraService<SimpleOrderInfo>,
        par: Int
    ) = coroutineScope {
        val sem = Semaphore(par)
        for (slice in orders.chunked(batch)) launch(Dispatchers.IO) {
            sem.acquire(); try {
            retry { svc.save(slice) }
        } finally {
            sem.release()
        }
        }
    }

    private fun sizeRowFor(
        table: String,
        blockB: Int,
        chunkK: Int,
        batch: Int
    ): DoubleArray {
        val sizeKiB = sizeRp.fetchTableSizes(table)[table]!! / 1024.0
        val sz = DoubleArray(4)
        when (table) {
            SimpleTable -> sz[0] = sizeKiB
            CassandraCompressedTable -> sz[1] = sizeKiB
            PrecompressedTable -> sz[2] = sizeKiB
            AppCompressedTable -> sz[3] = sizeKiB
        }
        report.writeSizes(blockB / 1024, chunkK, batch, sz)
        return sz
    }

    private fun persist(
        strategy: String,
        blockB: Int,
        chunkK: Int,
        batch: Int,
        write: Phase,
        read: Phase
    ) {
        fun Phase.persist(phaseName: String) =
            report.persistMetrics(
                strategy,
                blockB / 1024,
                chunkK,
                batch,
                phaseName,
                this.msOpP95,
                this.tpsP95,
                this.cpuP95
            )

        write.persist("write")
        read.persist("read")
    }

    private fun truncateAll() = listOf(
        SimpleTable, CassandraCompressedTable, PrecompressedTable, AppCompressedTable
    ).forEach { cql.execute("TRUNCATE dissertation.$it") }

    private fun alterDeflate(chunkKb: Int) =
        listOf(CassandraCompressedTable, PrecompressedTable).forEach {
            cql.execute(
                "ALTER TABLE dissertation.$it " +
                        "WITH compression={'class':'org.apache.cassandra.io.compress.DeflateCompressor'," +
                        "'chunk_length_in_kb':$chunkKb}"
            )
        }
}