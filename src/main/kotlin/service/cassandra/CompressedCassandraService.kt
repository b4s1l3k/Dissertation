package main.service.cassandra

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import main.data.CompressedOrderInfo
import main.data.OrderInfo
import main.service.compression.CompressionService
import main.utils.SerializationService
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class CompressedCassandraService(
    private val repository: CassandraRepository<CompressedOrderInfo, String>,
    private val compression: CompressionService,
    private val json: SerializationService
) : CassandraService {


    override suspend fun save(entities: List<OrderInfo>): Unit = withContext(Dispatchers.IO) {
        val sem = Semaphore(4)
        val compressed = coroutineScope {
            entities.map { order ->
                async(Dispatchers.Default) {
                    sem.acquire()
                    println("Сохраняется ${entities.size} записей")
                    try {
                        val bytes = json.serializeToBytes(order)
                        CompressedOrderInfo(order.id, compression.compressData(bytes))
                    } finally {
                        sem.release()
                    }
                }
            }.awaitAll()
        }
        repository.saveAll(compressed)
    }


    override suspend fun findById(id: String): OrderInfo? {
        val compressed = withContext(Dispatchers.IO) {
            repository.findById(id).orElse(null)
        }
        return compressed?.let { decompress(it) }
    }

    override suspend fun findAll(pageSize: Int): Unit = withContext(Dispatchers.IO) {
        var page = repository.findAll(PageRequest.of(0, pageSize))
        while (true) {
            page.content.forEach { compressed ->
                decompress(compressed)
            }
            if (!page.hasNext()) break
            page = repository.findAll(page.nextPageable())
        }
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        repository.deleteAll()
    }

    private suspend fun decompress(compressedOrderInfo: CompressedOrderInfo): OrderInfo =
        withContext(Dispatchers.Default) {
            val bytes = compression.decompressData(compressedOrderInfo.compressedPayload)
            json.deserializeFromBytes(bytes, OrderInfo::class.java)
        }
}
