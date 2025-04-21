package main.runner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import main.service.cassandra.CassandraService
import main.service.generator.DataGenerationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.exitProcess

private const val Threads = 10

@Service
class CassandraCompressedRunner(
    private val generator: DataGenerationService,
    @Qualifier("simpleCassandraService")
    private val cassandra: CassandraService
) {
    fun cassandraCompressedRun(ordersCount: Int, perCall: Int, randomCount: Boolean): Nothing = runBlocking {
        cassandra.deleteAll()
        println("Таблица очищена")

        val startTime = System.currentTimeMillis()

        val orders = AtomicInteger(ordersCount)

        val pipelines = List(Threads) {
            launch(Dispatchers.Default) {
                while (true) {
                    val orderInfoNumber = if (randomCount) Random.nextInt(10, 300) else perCall

                    if (orders.getAndAdd(-orderInfoNumber) <= 0) break

                    cassandra.save(
                        generator.generateOrders(orderInfoNumber)
                    )
                }
            }
        }

        pipelines.forEach { it.join() }

        cassandra.findAll(5000)

        val totalSeconds = (System.currentTimeMillis() - startTime) / 1000

        if (totalSeconds < 60) {
            println("Обработка заказов завершена!\nВремени потрачено: $totalSeconds с")
        } else {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            println("Обработка заказов завершена!\nВремени потрачено $minutes м $seconds с")
        }

        exitProcess(0)
    }
}