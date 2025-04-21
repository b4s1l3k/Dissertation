package main.service.cassandra

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import main.data.PreCompressedOrderInfo
import main.data.SimpleOrderInfo
import main.service.compression.CompressionService
import main.utils.SerializationService
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service("preCompressedCassandraService")
class PreCompressedCassandraService(
    private val repository: CassandraRepository<PreCompressedOrderInfo, String>,
    private val compression: CompressionService,
    private val json: SerializationService
) : CassandraService<SimpleOrderInfo> {

    override suspend fun save(entities: List<SimpleOrderInfo>): Unit = withContext(Dispatchers.IO) {
        val sem = Semaphore(4)
        val compressedEntities = coroutineScope {
            entities.map { order ->
                async(Dispatchers.Default) {
                    sem.acquire()
                    try {
                        val bytes = json.serializeToBytes(order)
                        PreCompressedOrderInfo(order.id, compression.compressData(bytes))
                    } finally {
                        sem.release()
                    }
                }
            }.awaitAll()
        }
        repository.saveAll(compressedEntities)
    }

    override suspend fun findById(id: String): SimpleOrderInfo? = withContext(Dispatchers.IO) {
        repository.findById(id).orElse(null)?.let { decompress(it) }
    }

    override suspend fun findAll(pageSize: Int): Unit = withContext(Dispatchers.Default) {
        var page = withContext(Dispatchers.IO) { repository.findAll(PageRequest.of(0, pageSize)) }
        do {
            page.content.forEach { decompress(it) }
            if (!page.hasNext()) break
            page = withContext(Dispatchers.IO) { repository.findAll(page.nextPageable()) }
        } while (true)
    }

    override suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        repository.deleteAll()
    }

    private suspend fun decompress(entity: PreCompressedOrderInfo): SimpleOrderInfo =
        withContext(Dispatchers.Default) {
            val bytes = compression.decompressData(entity.compressedPayload)
            json.deserializeFromBytes(bytes, SimpleOrderInfo::class.java)
        }
}
