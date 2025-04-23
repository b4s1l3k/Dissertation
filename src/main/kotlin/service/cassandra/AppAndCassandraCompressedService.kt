package main.service.cassandra

import kotlinx.coroutines.*
import main.data.PreCompressedOrderInfo
import main.data.SimpleOrderInfo
import main.service.compression.CompressionService
import main.utils.SerializationService
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service("appAndCassandraCompressedService")
class AppAndCassandraCompressedService(
    private val repository: CassandraRepository<PreCompressedOrderInfo, String>,
    private val compression: CompressionService,
    private val serializer: SerializationService
) : CassandraService<SimpleOrderInfo> {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cpuBound =
        Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors())

    private val baosPool = ArrayDeque<ByteArrayOutputStream>()

    private fun borrowStream(): ByteArrayOutputStream =
        synchronized(baosPool) { baosPool.removeFirstOrNull() ?: ByteArrayOutputStream(4 * 1024) }

    private fun releaseStream(bos: ByteArrayOutputStream) =
        synchronized(baosPool) { baosPool.addLast(bos) }

    override suspend fun save(entities: List<SimpleOrderInfo>): Unit = coroutineScope {
        val blobs = entities.map { order ->
            async(cpuBound) {
                val bos = borrowStream()
                try {
                    bos.reset()
                    serializer.serializeToStream(order, bos)
                    val compressed = compression.compressData(bos.toByteArray())
                    PreCompressedOrderInfo(order.id, compressed)
                } finally {
                    releaseStream(bos)
                }
            }
        }.awaitAll()

        withContext(Dispatchers.IO) {
            repository.saveAll(blobs)
        }
    }

    override suspend fun findById(id: String): SimpleOrderInfo? =
        withContext(Dispatchers.IO) { repository.findById(id).orElse(null) }
            ?.let { entry ->
                withContext(cpuBound) {
                    val bytes = compression.decompressData(entry.compressedPayload)
                    serializer.deserializeFromBytes(bytes, SimpleOrderInfo::class.java)
                }
            }

    override suspend fun findByIds(ids: List<String>): List<SimpleOrderInfo?> = coroutineScope {
        val entriesById: Map<String, PreCompressedOrderInfo> = withContext(Dispatchers.IO) {
            repository.findAllById(ids).associateBy { it.id }
        }

        ids.map { id ->
            entriesById[id]?.let { entry ->
                async(cpuBound) {
                    val decompressed = compression.decompressData(entry.compressedPayload)
                    serializer.deserializeFromBytes(decompressed, SimpleOrderInfo::class.java)
                }
            }
        }.mapNotNull { it?.await() }
    }

    override suspend fun findAll(pageSize: Int) = coroutineScope {
        var page = withContext(Dispatchers.IO) { repository.findAll(PageRequest.of(0, pageSize)) }

        while (true) {
            page.content.map { entry ->
                async(cpuBound) {
                    val bytes = compression.decompressData(entry.compressedPayload)
                    serializer.deserializeFromBytes(bytes, SimpleOrderInfo::class.java)
                }
            }.awaitAll()

            page = if (page.hasNext())
                withContext(Dispatchers.IO) { repository.findAll(page.nextPageable()) }
            else
                break
        }
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) { repository.deleteAll() }
}
