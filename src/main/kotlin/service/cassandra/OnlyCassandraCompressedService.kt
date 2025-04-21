package main.service.cassandra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import main.data.CassandraOrderInfo
import main.data.SimpleOrderInfo
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service("onlyCassandraCompressedService")
class OnlyCassandraCompressedService(
    private val repo: CassandraRepository<CassandraOrderInfo, String>
) : CassandraService<SimpleOrderInfo> {

    override suspend fun save(entities: List<SimpleOrderInfo>): Unit =
        withContext(Dispatchers.IO) {
            repo.saveAll(entities.map(SimpleOrderInfo::toEntity))
        }

    override suspend fun findById(id: String): SimpleOrderInfo? =
        withContext(Dispatchers.IO) {
            repo.findById(id).orElse(null)?.toDto()
        }

    override suspend fun findAll(pageSize: Int) = coroutineScope {
        var page = repo.findAll(PageRequest.of(0, pageSize))
        do {
            page.content.forEach { it.toDto() }
            if (!page.hasNext()) break
            page = repo.findAll(page.nextPageable())
        } while (true)
    }

    override suspend fun deleteAll() =
        withContext(Dispatchers.IO) { repo.deleteAll() }
}

private fun SimpleOrderInfo.toEntity() = CassandraOrderInfo(
    id, user, payment, product, quantity, totalPrice,
    orderDate, status, deliveryMethod, deliveryAddress,
    estimatedDeliveryDate, trackingNumber, paymentId,
    discountAmount, taxAmount, metadata
)

private fun CassandraOrderInfo.toDto() = SimpleOrderInfo(
    id, user, payment, product, quantity, totalPrice,
    orderDate, status, deliveryMethod, deliveryAddress,
    estimatedDeliveryDate, trackingNumber, paymentId,
    discountAmount, taxAmount, metadata
)