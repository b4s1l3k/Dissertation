package main.benchmark

import kotlinx.coroutines.*
import main.service.cassandra.CassandraService
import main.service.generator.DataGenerationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private const val THREADS = 10
private val PageSizes = listOf(500, 1000, 5000, 10000)

@Service
class CassandraFindAllBenchmark(
    private val generator: DataGenerationService,
    @Qualifier("compressedCassandraService")
    private val compressed: CassandraService,
    @Qualifier("simpleCassandraService")
    private val simple: CassandraService,
) {

    /**
     * Прогоняет оба сервиса и в конце выводит общую сводку.
     */
    fun runBenchmark(
        ordersCount: Int,
        perCall: Int,
        randomCount: Boolean
    ): Nothing = runBlocking {
        val summary = linkedMapOf<String, List<Pair<Int, Long>>>()

        summary["Compressed"] = benchmark(
            "CompressedCassandraService",
            compressed,
            ordersCount, perCall, randomCount
        )

        summary["Simple"] = benchmark(
            "SimpleCassandraService",
            simple,
            ordersCount, perCall, randomCount
        )

        // ────────── итоговая таблица ──────────
        println("\n=== Сводные результаты ===")
        // Заголовок
        print("pageSize".padEnd(10))
        summary.keys.forEach { label -> print("| ${label.padEnd(10)}") }
        println("\n" + "-".repeat(11 + summary.size * 13))

        PageSizes.forEach { size ->
            print(size.toString().padEnd(10))
            summary.values.forEach { list ->
                val ms = list.first { it.first == size }.second
                print("| ${"%.3f".format(ms / 1_000.0).padEnd(10)}")
            }
            println()
        }

        println("\nBenchmark завершён.")
        exitProcess(0)
    }

    private suspend fun benchmark(
        label: String,
        cassandra: CassandraService,
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
            println("pageSize=$size → ${"%.3f".format(ms / 1_000.0)} сек")
        }
        return results
    }
}
