package main.runner

import kotlinx.coroutines.Dispatchers
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

private const val THREADS = 10

@Service
class PreCompressedRunner(
    private val generator: DataGenerationService,
    @Qualifier("appAndCassandraCompressedService")
    private val cassandra: CassandraService<SimpleOrderInfo>
) {
    fun preCompresedRun(ordersCount: Int, perCall: Int, randomCount: Boolean): Nothing = runBlocking {
        cassandra.deleteAll()
        println("Таблица очищена")

        val startTime = System.currentTimeMillis()
        val remaining = AtomicInteger(ordersCount)

        val jobs = List(THREADS) {
            launch(Dispatchers.Default) {
                while (true) {
                    val batch = if (randomCount) Random.nextInt(10, 300) else perCall
                    if (remaining.getAndAdd(-batch) <= 0) break
                    cassandra.save(generator.generateOrders(batch))
                }
            }
        }
        jobs.forEach { it.join() }

        cassandra.findAll(5_000)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        if (elapsed < 60) {
            println("Обработка заказов завершена! Времени потрачено: $elapsed с")
        } else {
            println("Обработка заказов завершена! Времени потрачено: ${elapsed / 60} м ${elapsed % 60} с")
        }

        exitProcess(0)
    }
}