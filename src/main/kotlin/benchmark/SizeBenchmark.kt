//package main.benchmark
//
//import kotlinx.coroutines.*
//import kotlinx.coroutines.sync.Semaphore
//import main.benchmark.utils.BenchmarkReportService
//import main.data.SimpleOrderInfo
//import main.service.cassandra.CassandraService
//import main.service.cassandra.utils.TableSizeReporter
//import main.service.compression.SnappyCompressionProtocol
//import main.service.generator.DataGenerationService
//import main.utils.Retry.retry
//import org.springframework.beans.factory.annotation.Qualifier
//import org.springframework.data.cassandra.core.cql.CqlTemplate
//import org.springframework.stereotype.Service
//import kotlin.system.exitProcess
//
//private const val SimpleTable = "simple_order_info"
//private const val CassandraTable = "cassandra_order_info"
//private const val PrecompressedTable = "precompressed_order_info"
//private const val AppCompressedTable = "app_compressed_order_info"
//
//@Service
//class SizeBenchmark(
//    private val generator: DataGenerationService,
//    private val cql: CqlTemplate,
//    private val snappy: SnappyCompressionProtocol,
//    private val sizeReporter: TableSizeReporter,
//    private val report: BenchmarkReportService,
//
//    @Qualifier("simpleCassandraService")
//    private val simpleSvc: CassandraService<SimpleOrderInfo>,
//    @Qualifier("cassandraCompressedService")
//    private val cassSvc: CassandraService<SimpleOrderInfo>,
//    @Qualifier("appCompressedService")
//    private val appSvc: CassandraService<SimpleOrderInfo>,
//    @Qualifier("appAndCassandraCompressedService")
//    private val precompSvc: CassandraService<SimpleOrderInfo>,
//) {
//
//    private enum class Tbl(val tableName: String) {
//        SIMPLE(SimpleTable), CASS(CassandraTable),
//        APPC(AppCompressedTable), PRE(PrecompressedTable)
//    }
//
//    private val svcByTbl = mapOf(
//        Tbl.SIMPLE to simpleSvc,
//        Tbl.CASS to cassSvc,
//        Tbl.APPC to appSvc,
//        Tbl.PRE to precompSvc
//    )
//
//    fun runSizeBenchmark(
//        ordersCount: Int,
//        blockSizes: List<Int>,
//        chunkSizes: List<Int>
//    ) = runBlocking {
//        println("=== Generating $ordersCount random orders ===")
//        val startTime = System.currentTimeMillis()
//
//        val orders = generator.generateOrders(ordersCount)
//
//        blockSizes.forEachIndexed { bsIdx, blockB ->
//            snappy.blockSize = blockB
//            println("\n>>> Snappy.blockSize = $blockB B")
//
//            chunkSizes.forEachIndexed { ckIdx, chunkKb ->
//
//                val toTest = buildSet {
//                    if (bsIdx == 0 && ckIdx == 0) add(Tbl.SIMPLE)
//                    if (bsIdx == 0) add(Tbl.CASS)
//                    if (ckIdx == 0) add(Tbl.APPC)
//                    add(Tbl.PRE)
//                }
//
//                truncateAndAlter(chunkKb)
//                preload(orders, toTest)
//
//                println("--- block=$blockB B | chunk=${chunkKb} KiB ---")
//
//                val sizes = sizeReporter.fetchTableSizes(*toTest.map { it.tableName }.toTypedArray())
//
//                toTest.forEach { tbl ->
//                    val bytes = sizes.getValue(tbl.tableName)
//                    println(" ${tbl.tableName.padEnd(25)} : $bytes bytes (~${bytes / 1024} KiB)")
//                }
//
//                val row = DoubleArray(4)
//                row[Tbl.SIMPLE.ordinal] = sizes[Tbl.SIMPLE.tableName]?.div(1024.0) ?: 0.0
//                row[Tbl.CASS.ordinal] = sizes[Tbl.CASS.tableName]?.div(1024.0) ?: 0.0
//                row[Tbl.PRE.ordinal] = sizes[Tbl.PRE.tableName]?.div(1024.0) ?: 0.0
//                row[Tbl.APPC.ordinal] = sizes[Tbl.APPC.tableName]?.div(1024.0) ?: 0.0
//
//                report.writeSizes(blockB / 1024, chunkKb, row)
//            }
//        }
//        report.saveTo("size_results.xlsx")
//
//        val elapsed = (System.currentTimeMillis() - startTime) / 1000
//        println("⏱  Total time: ${elapsed / 60} m ${elapsed % 60} s")
//    }
//
//    private fun truncateAndAlter(chunkKb: Int) {
//
//        listOf(SimpleTable, CassandraTable, PrecompressedTable, AppCompressedTable).forEach {
//            cql.execute("TRUNCATE dissertation.$it")
//        }
//
//        listOf(CassandraTable, PrecompressedTable).forEach {
//            cql.execute(
//                "ALTER TABLE dissertation.$it " +
//                        "WITH compression={'class':'org.apache.cassandra.io.compress.DeflateCompressor'," +
//                        "'chunk_length_in_kb':$chunkKb}"
//            )
//        }
//        sizeReporter.clearSnapshots()
//    }
//
//    private suspend fun preload(
//        orders: List<SimpleOrderInfo>,
//        tables: Set<Tbl>,
//        parallelism: Int = Runtime.getRuntime().availableProcessors()
//    ) = coroutineScope {
//        val sem = Semaphore(parallelism)
//
//        println("   Preloading ${tables.size} tables…")
//        tables.forEach { tbl ->
//            val svc = svcByTbl.getValue(tbl)
//            println("      → ${tbl.tableName}")
//            svc.deleteAll()
//            val chunkSize = (orders.size + 9) / parallelism
//            orders.chunked(chunkSize).forEach { chunk ->
//                launch(Dispatchers.IO) {
//                    sem.acquire()
//                    try {
//                        retry { svc.save(chunk) }
//                    } finally {
//                        sem.release()
//                    }
//                }
//            }
//        }
//    }
//}
