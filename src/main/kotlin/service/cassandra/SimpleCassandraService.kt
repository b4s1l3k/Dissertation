package main.service.cassandra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import main.data.OrderInfo
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class SimpleCassandraService(
    private val repository: CassandraRepository<OrderInfo, String>
) : CassandraService {

    override suspend fun save(entities: List<OrderInfo>): Unit = withContext(Dispatchers.IO) {
        println("Сохраняется ${entities.size} записей")
        repository.saveAll(entities)
    }

    override suspend fun findById(id: String): OrderInfo? = withContext(Dispatchers.IO) {
        repository.findById(id).orElse(null)
    }

    override suspend fun findAll(pageSize: Int): Unit = withContext(Dispatchers.IO) {
        var page = repository.findAll(PageRequest.of(0, pageSize))
        while (true) {
            if (!page.hasNext()) break
            page = repository.findAll(page.nextPageable())
        }
    }

    override suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        repository.deleteAll()
    }
}