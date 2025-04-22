package main.service.cassandra

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import main.data.CompressedOrderInfo
import main.data.SimpleOrderInfo
import main.service.compression.CompressionService
import main.utils.SerializationService
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service("appCompressedService")
class AppCompressedService(
    private val repository: CassandraRepository<CompressedOrderInfo, String>,
    private val compression: CompressionService,
    private val serializer: SerializationService
) : CassandraService<SimpleOrderInfo> {
    override suspend fun save(entities: List<SimpleOrderInfo>): Unit = withContext(Dispatchers.IO) {
        val sem = Semaphore(4)
        val blobs = coroutineScope {
            entities.map { order ->
                async(Dispatchers.Default) {
                    sem.acquire()
                    try {
                        val bytes = serializer.serializeToBytes(order)
                        CompressedOrderInfo(order.id, compression.compressData(bytes))
                    } finally {
                        sem.release()
                    }
                }
            }.awaitAll()
        }
        repository.saveAll(blobs)
    }

    override suspend fun findById(id: String): SimpleOrderInfo? = withContext(Dispatchers.IO) {
        repository.findById(id)
            .orElse(null)
            ?.let { entry ->
                val decrypted = compression.decompressData(entry.compressedPayload)
                serializer.deserializeFromBytes(decrypted, SimpleOrderInfo::class.java)
            }
    }

    override suspend fun findAll(pageSize: Int) = withContext(Dispatchers.Default) {
        var page = withContext(Dispatchers.IO) { repository.findAll(PageRequest.of(0, pageSize)) }
        do {
            page.content.forEach { entry ->
                val decrypted = compression.decompressData(entry.compressedPayload)
                serializer.deserializeFromBytes(decrypted, SimpleOrderInfo::class.java)
            }
            page =
                if (page.hasNext()) withContext(Dispatchers.IO) { repository.findAll(page.nextPageable()) } else break
        } while (true)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) { repository.deleteAll() }
}
