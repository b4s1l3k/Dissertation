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

private const val DoubleCompressedStrategy = "DoubleCompressed"
private const val AppCompressedStrategy = "AppCompressed"
private const val CassCompressedStrategy = "CassCompressed"
private const val SimpleStrategy = "Simple"

private val snappyStrategies = setOf(AppCompressedStrategy, DoubleCompressedStrategy)
private val deflateStrategies = setOf(CassCompressedStrategy, DoubleCompressedStrategy)

private const val SimpleTable = "simple_order_info"
private const val CassandraCompressedTable = "cassandra_order_info"
private const val AppCompressedTable = "app_compressed_order_info"
private const val PrecompressedTable = "precompressed_order_info"

private val workbook = XSSFWorkbook()
private val sheet = workbook.createSheet("Results")

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
        val memMiB: Double
    )

    private val services = mapOf(
        SimpleStrategy to simpleService,
        CassCompressedStrategy to cassandraCompressedService,
        AppCompressedStrategy to appCompressedService,
        DoubleCompressedStrategy to doubleCompressedService
    )

    private var rowIdx = 0

    init {
        sheet.createRow(rowIdx++).apply {
            listOf(
                "strategy", "blockKiB", "chunkKiB", "batch", "readRatio",
                "ms/op_P95", "tps_P95", "cpu_P95", "mem_P95_MiB",
                "simple_KiB", "cassandra_KiB", "precomp_KiB", "appcomp_KiB"
            ).forEachIndexed { i, h -> createCell(i).setCellValue(h) }
        }
    }

    private val batchSheets = mutableMapOf<Int, org.apache.poi.ss.usermodel.Sheet>()
    private val batchRowIndex = mutableMapOf<Int, Int>()
    private val headerRow = sheet.getRow(0)

    private fun sheetForBatch(batch: Int): org.apache.poi.ss.usermodel.Sheet =
        batchSheets.getOrPut(batch) {
            val sh = workbook.createSheet("batch_$batch")

            val hdr = sh.createRow(0)
            for (i in 0 until headerRow.lastCellNum) {
                hdr.createCell(i).setCellValue(headerRow.getCell(i).stringCellValue)
            }
            batchRowIndex[batch] = 1
            sh
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

        println("\n=== Generating $ordersCount random orders ===")
        val startTime = System.currentTimeMillis()

        val orders = generator.generateOrders(ordersCount)
            .mapIndexed { i, o -> o.copy(id = "${o.id}_$i") }

        blockSizes.forEachIndexed { bsIdx, block ->
            snappy.blockSize = block
            println("\n>>> Snappy.blockSize = $block B")

            chunkSizes.forEachIndexed { ckIdx, chunkKb ->
                println("\n → Deflate chunk_length_in_kb = $chunkKb KiB")
                alterDeflate(chunkKb)

                val toTest = services.filter { (strategy, _) ->
                    val usesSnappy = strategy in snappyStrategies
                    val usesDeflate = strategy in deflateStrategies

                    val blockOk = usesSnappy || strategy in deflateStrategies || bsIdx == 0
                    val chunkOk = usesDeflate || ckIdx == 0

                    blockOk && chunkOk
                }

                preload(orders, parallelBursts, toTest)

                val sizesKiB = sizeReporter.fetchTableSizes(
                    SimpleTable,
                    CassandraCompressedTable,
                    PrecompressedTable,
                    AppCompressedTable
                ).mapValues { it.value / 1024.0 }

                val sizeRow = doubleArrayOf(
                    sizesKiB[SimpleTable] ?: 0.0,
                    sizesKiB[CassandraCompressedTable] ?: 0.0,
                    sizesKiB[PrecompressedTable] ?: 0.0,
                    sizesKiB[AppCompressedTable] ?: 0.0
                )

                toTest.forEach { (strategy, svc) ->
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
                                seed = seed,
                                sizeRow = sizeRow
                            )
                        }
                    }
                }
            }
        }

        FileOutputStream("benchmark_results.xlsx").use { workbook.write(it) }
        workbook.close()
        println("\n✅  All benchmarks completed; results saved to benchmark_results.xlsx")

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        if (elapsed < 60) {
            println("\nВремени потрачено: $elapsed с")
        } else {
            println("\nВремени потрачено: ${elapsed / 60} м ${elapsed % 60} с")
        }

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
        listOf(CassandraCompressedTable, AppCompressedTable).forEach { tbl ->
            cql.execute(
                "ALTER TABLE dissertation.$tbl " +
                        "WITH compression={'class':'org.apache.cassandra.io.compress.DeflateCompressor'," +
                        "'chunk_length_in_kb':$chunkKb}"
            )
            verifyChunkLength(tbl, chunkKb)
        }
    }

    private fun verifyChunkLength(tableName: String, expected: Int) {
        val opts = fetchCompressionOptions(tableName)
        val actual = opts["chunk_length_in_kb"]?.toIntOrNull()
            ?: error("У таблицы $tableName нет опции chunk_length_in_kb в compression")
        if (actual != expected) {
            error("Неправильный chunk_length_in_kb для $tableName: ожидается $expected, а реально $actual")
        }
    }

    private fun fetchCompressionOptions(tableName: String): Map<String, String> {
        val row = cql.queryForList(
            "SELECT compression FROM system_schema.tables WHERE keyspace_name=? AND table_name=?",
            "dissertation", tableName
        ).firstOrNull() ?: error("Не найдена информация о таблице $tableName в system_schema.tables")
        @Suppress("UNCHECKED_CAST")
        return row["compression"] as? Map<String, String>
            ?: error("Поле compression у таблицы $tableName отсутствует или имеет неожиданный тип")
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
        seed: Long,
        sizeRow: DoubleArray
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
                    try {
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
                            memMiB = m.totalUsedMiB
                        )
                    } finally {
                        sem.release()
                    }
                    delay(interDelayMs)
                }
            }
        }

        persistMetrics(
            strategy, blockSize, chunkKb, batchSize, readRatio,
            stats, sizeRow
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

        val readCount = batchSize - writes
        val readIds = List(readCount) { orders.random(rnd).id }

        svc.findByIds(readIds)
    }

    private fun persistMetrics(
        strategy: String,
        block: Int,
        chunkKb: Int,
        batch: Int,
        readRatio: Double,
        results: List<BurstResult>,
        sizeRow: DoubleArray
    ) {
        fun List<Double>.p95() = this[(size * 0.95).toInt().coerceAtMost(lastIndex)]

        val perOp = results.map { it.msPerOp }.sorted().p95()
        val tps = results.map { it.tps }.sorted().p95()
        val cpu = results.map { it.cpu }.sorted().p95()
        val mem = results.map { it.memMiB }.sorted().p95()

        sheet.createRow(rowIdx++).apply {
            createCell(0).setCellValue(strategy)
            createCell(1).setCellValue(block.toDouble()/1024)
            createCell(2).setCellValue(chunkKb.toDouble())
            createCell(3).setCellValue(batch.toDouble())
            createCell(4).setCellValue(readRatio)
            createCell(5).setCellValue(perOp)
            createCell(6).setCellValue(tps)
            createCell(7).setCellValue(cpu)
            createCell(8).setCellValue(mem)
            createCell(9).setCellValue(sizeRow[0])
            createCell(10).setCellValue(sizeRow[1])
            createCell(11).setCellValue(sizeRow[2])
            createCell(12).setCellValue(sizeRow[3])
        }

        val sh = sheetForBatch(batch)
        val idxB = batchRowIndex.getValue(batch)
        sh.createRow(idxB).apply {
            createCell(0).setCellValue(strategy)
            createCell(1).setCellValue(block.toDouble()/1024)
            createCell(2).setCellValue(chunkKb.toDouble())
            createCell(3).setCellValue(batch.toDouble())
            createCell(4).setCellValue(readRatio)
            createCell(5).setCellValue(perOp)
            createCell(6).setCellValue(tps)
            createCell(7).setCellValue(cpu)
            createCell(8).setCellValue(mem)
            createCell(9).setCellValue(sizeRow[0])
            createCell(10).setCellValue(sizeRow[1])
            createCell(11).setCellValue(sizeRow[2])
            createCell(12).setCellValue(sizeRow[3])
        }
        batchRowIndex[batch] = idxB + 1
    }
}
