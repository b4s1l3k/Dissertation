package main.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import main.data.SimpleOrderInfo
import main.service.cassandra.CassandraService
import main.service.generator.DataGenerationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private const val THREADS = 10
private val PageSizes = listOf(500, 1000, 5000, 10_000)

@Service
class CassandraFindAllBenchmark(
    private val generator: DataGenerationService,

    @Qualifier("simpleCassandraService")
    private val simple: CassandraService<SimpleOrderInfo>,

    @Qualifier("compressedCassandraService")
    private val compressed: CassandraService<SimpleOrderInfo>,

    @Qualifier("onlyCassandraCompressedService")
    private val onlyCompressed: CassandraService<SimpleOrderInfo>,

    @Qualifier("preCompressedCassandraService")
    private val preCompressed: CassandraService<SimpleOrderInfo>,
) {

    fun runBenchmark(
        ordersCount: Int,
        perCall: Int,
        randomCount: Boolean
    ): Nothing = runBlocking {
        val summary = linkedMapOf<String, List<Pair<Int, Long>>>()

        summary["Simple"] = benchmark("Simple", simple, ordersCount, perCall, randomCount)
        summary["Compressed"] = benchmark("Compressed", compressed, ordersCount, perCall, randomCount)
        summary["OnlyCompressed"] = benchmark("OnlyCompressed", onlyCompressed, ordersCount, perCall, randomCount)
        summary["PreCompressed"] = benchmark("PreCompressed", preCompressed, ordersCount, perCall, randomCount)

        println("\n=== Сводные результаты ===")
        print("pageSize".padEnd(12))
        summary.keys.forEach { label -> print("| ${label.padEnd(15)}") }
        println("\n" + "-".repeat(13 + summary.size * 18))

        PageSizes.forEach { size ->
            print(size.toString().padEnd(12))
            summary.values.forEach { list ->
                val ms = list.first { it.first == size }.second
                print("| ${"%.3f".format(ms / 1_000.0).padEnd(15)}")
            }
            println()
        }

        println("\nBenchmark завершён.")
        exitProcess(0)
    }

    private suspend fun benchmark(
        label: String,
        cassandra: CassandraService<SimpleOrderInfo>,
        ordersCount: Int,
        perCall: Int,
        randomCount: Boolean
    ): List<Pair<Int, Long>> {
        println("\n=== $label ===")
        cassandra.deleteAll()

        val remaining = AtomicInteger(ordersCount)
        coroutineScope {
            repeat(THREADS) {
                launch(Dispatchers.Default) {
                    while (true) {
                        val batch = if (randomCount) Random.nextInt(10, 300) else perCall
                        if (remaining.getAndAdd(-batch) <= 0) break
                        cassandra.save(generator.generateOrders(batch))
                    }
                }
            }
        }

        val results = mutableListOf<Pair<Int, Long>>()
        for (size in PageSizes) {
            val ms = measureTimeMillis { cassandra.findAll(size) }
            results += size to ms
            println("$label pageSize=$size → ${"%.3f".format(ms / 1_000.0)} сек")
        }
        return results
    }
}
