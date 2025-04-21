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
    private val repo: CassandraRepository<SimpleOrderInfo, String>
) : CassandraService<SimpleOrderInfo> {

    override suspend fun save(entities: List<SimpleOrderInfo>): Unit = withContext(Dispatchers.IO) {
        repo.saveAll(entities)
    }

    override suspend fun findById(id: String): SimpleOrderInfo? = withContext(Dispatchers.IO) {
        repo.findById(id).orElse(null)
    }

    override suspend fun findAll(pageSize: Int): Unit = coroutineScope {
        var page = repo.findAll(PageRequest.of(0, pageSize))

        do {
            page.content.forEach { /* no‑op: доступ к данным для честного замера */ }
            if (!page.hasNext()) break
            page = repo.findAll(page.nextPageable())
        } while (true)
    }

    override suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        repo.deleteAll()
    }
}