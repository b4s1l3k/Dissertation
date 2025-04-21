package main.runner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import main.data.NonCompressedOrderInfo
import main.data.NonCompressedOrderInfoRepository
import main.data.OrderInfo
import main.service.cassandra.CassandraService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service("nonCompressedCassandraService")
class NonCompressedCassandraService(
    private val repo: NonCompressedOrderInfoRepository
) : CassandraService {

    override suspend fun save(entities: List<OrderInfo>): Unit = withContext(Dispatchers.IO) {
        println("Сохраняется ${entities.size} без компрессии")
        repo.saveAll(entities.map { it.toEntity() })
    }

    override suspend fun findById(id: String): OrderInfo? = withContext(Dispatchers.IO) {
        repo.findById(id).orElse(null)?.toDomain()
    }

    override suspend fun findAll(pageSize: Int) = withContext(Dispatchers.IO) {
        var page = repo.findAll(PageRequest.of(0, pageSize))
        while (true) {
            page.content.forEach { it.toDomain() }
            if (!page.hasNext()) break
            page = repo.findAll(page.nextPageable())
        }
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) { repo.deleteAll() }
}
