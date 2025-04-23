package main.service.cassandra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import main.data.SimpleOrderInfo
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service("simpleCassandraService")
class SimpleCassandraService(
    private val repository: CassandraRepository<SimpleOrderInfo, String>
) : CassandraService<SimpleOrderInfo> {

    override suspend fun save(entities: List<SimpleOrderInfo>): Unit = withContext(Dispatchers.IO) {
        repository.saveAll(entities)
    }

    override suspend fun findById(id: String): SimpleOrderInfo? = withContext(Dispatchers.IO) {
        repository.findById(id).orElse(null)
    }

    override suspend fun findByIds(ids: List<String>): List<SimpleOrderInfo?> =
        withContext(Dispatchers.IO) {
            repository.findAllById(ids).map { it }
        }

    override suspend fun findAll(pageSize: Int): Unit = coroutineScope {
        var page = repository.findAll(PageRequest.of(0, pageSize))

        do {
            page.content.forEach { }
            if (!page.hasNext()) break
            page = repository.findAll(page.nextPageable())
        } while (true)
    }

    override suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        repository.deleteAll()
    }
}