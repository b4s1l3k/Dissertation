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

    private data class PhaseResult(
        val phase: String,
        val timeMs: Long,
        val tps: Double,
        var cpu: Double
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
                "phase",
                "ms/op_P95", "tps_P95", "cpu_P95",
                "simple_MiB", "cassandra_MiB", "precomp_MiB", "appcomp_MiB"
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
            for (i in (0 until headerRow.lastCellNum))
                hdr.createCell(i).setCellValue(headerRow.getCell(i).stringCellValue)
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
        seed: Long = 42L
    ): Unit = runBlocking {

        println("\n=== Generating $ordersCount random orders ===")
        val startTime = System.currentTimeMillis()

        val orders = generator.generateOrders(ordersCount)
            .mapIndexed { i, o -> o.copy(id = "${o.id}_$i") }

        blockSizes.forEachIndexed { bsIdx, block ->
            clearAllTables()

            snappy.blockSize = block
            println("\n>>> Snappy.blockSize = $block B")

            chunkSizes.forEachIndexed { ckIdx, chunkKb ->
                clearAllTables()

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
        println("\n⏱  Total time: ${elapsed / 60} m ${elapsed % 60}s")
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
        listOf(CassandraCompressedTable, PrecompressedTable).forEach { tbl ->
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

    private suspend fun clearAllTables() = coroutineScope {
        listOf(
            simpleService,
            cassandraCompressedService,
            appCompressedService,
            doubleCompressedService
        ).map { svc ->
            launch(Dispatchers.IO) { svc.deleteAll() }
        }.joinAll()
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

        val writesPerBurst = (batchSize * (1 - readRatio)).toInt().coerceAtLeast(1)
        val readsPerBurst = batchSize - writesPerBurst

        repeat(warmUpBursts) { i ->
            val rnd = Random(seed + i)
            svc.save(List(writesPerBurst) { orders.random(rnd) })
            svc.findById(orders.random(rnd).id)
            delay(warmUpDelayMs)
        }

        val writeStats = mutableListOf<PhaseResult>()
        val readStats = mutableListOf<PhaseResult>()
        val cpuWriteSamples = mutableListOf<Double>()
        val cpuReadSamples = mutableListOf<Double>()

        val sem = Semaphore(parallel)

        coroutineScope {
            repeat(bursts) { i ->
                launch(Dispatchers.IO) {
                    sem.acquire()
                    try {
                        val rnd = Random(seed + warmUpBursts + i)

                        val wStart = jvm.snap()
                        val wTime = measureTimeMillis {
                            svc.save(List(writesPerBurst) { orders.random(rnd) })
                        }
                        val wCpu = jvm.cpuLoadPct(wStart, jvm.snap())
                        writeStats += PhaseResult(
                            phase = "write",
                            timeMs = wTime,
                            tps = if (wTime > 0) writesPerBurst * 1_000.0 / wTime else 0.0,
                            cpu = wCpu
                        )
                        cpuWriteSamples += wCpu

                        val rStart = jvm.snap()
                        val rTime = measureTimeMillis {
                            val ids = List(readsPerBurst) { orders.random(rnd).id }
                            svc.findByIds(ids)
                        }
                        val rCpu = jvm.cpuLoadPct(rStart, jvm.snap())
                        readStats += PhaseResult(
                            phase = "read",
                            timeMs = rTime,
                            tps = if (rTime > 0) readsPerBurst * 1_000.0 / rTime else 0.0,
                            cpu = rCpu
                        )
                        cpuReadSamples += rCpu
                    } finally {
                        sem.release()
                    }
                    delay(interDelayMs)
                }
            }
        }

        val cpuWriteP95 = cpuWriteSamples.p95()
        val cpuReadP95 = cpuReadSamples.p95()
        writeStats.forEach { it.cpu = cpuWriteP95 }
        readStats.forEach { it.cpu = cpuReadP95 }

        persistMetrics(
            strategy = strategy,
            blockKiB = blockSize / 1024,
            chunkKiB = chunkKb,
            batch = batchSize,
            readRatio = readRatio,
            write = writeStats,
            read = readStats,
            sizeRow = sizeRow
        )
    }

    private fun List<Double>.p95(): Double =
        if (isEmpty()) Double.NaN
        else sorted()[((size * 0.95).toInt()).coerceAtMost(lastIndex)]

    private fun persistMetrics(
        strategy: String,
        blockKiB: Int,
        chunkKiB: Int,
        batch: Int,
        readRatio: Double,
        write: List<PhaseResult>,
        read: List<PhaseResult>,
        sizeRow: DoubleArray
    ) {
        listOf(write, read).forEach { stats ->
            val phase = stats.first().phase
            val perOpP95 = stats.map {
                it.timeMs / (if (phase == "write") (batch * (1 - readRatio))
                else (batch * readRatio)).coerceAtLeast(1.0)
            }.p95()
            val tpsP95 = stats.map { it.tps }.p95()
            val cpuP95 = stats.map { it.cpu }.p95()

            sheet.createRow(rowIdx++).apply {
                createCell(0).setCellValue(strategy)
                createCell(1).setCellValue(blockKiB.toDouble())
                createCell(2).setCellValue(chunkKiB.toDouble())
                createCell(3).setCellValue(batch.toDouble())
                createCell(4).setCellValue(readRatio)

                createCell(5).setCellValue(phase)

                createCell(6).setCellValue(perOpP95)
                createCell(7).setCellValue(tpsP95)
                createCell(8).setCellValue(cpuP95)

                createCell(9).setCellValue(sizeRow[0] / 1024)
                createCell(10).setCellValue(sizeRow[1] / 1024)
                createCell(11).setCellValue(sizeRow[2] / 1024)
                createCell(12).setCellValue(sizeRow[3] / 1024)
            }

            val sh = sheetForBatch(batch)
            val idx = batchRowIndex.getValue(batch)
            sh.createRow(idx).apply {
                createCell(0).setCellValue(strategy)
                createCell(1).setCellValue(blockKiB.toDouble())
                createCell(2).setCellValue(chunkKiB.toDouble())
                createCell(3).setCellValue(batch.toDouble())
                createCell(4).setCellValue(readRatio)
                createCell(5).setCellValue(phase)
                createCell(6).setCellValue(perOpP95)
                createCell(7).setCellValue(tpsP95)
                createCell(8).setCellValue(cpuP95)

                createCell(9).setCellValue(sizeRow[0] / 1024)
                createCell(10).setCellValue(sizeRow[1] / 1024)
                createCell(11).setCellValue(sizeRow[2] / 1024)
                createCell(12).setCellValue(sizeRow[3] / 1024)
            }
            batchRowIndex[batch] = idx + 1
        }
    }
}