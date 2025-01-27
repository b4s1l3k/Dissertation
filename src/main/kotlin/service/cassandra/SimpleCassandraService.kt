package main.service.cassandra

import main.data.OrderInfo
import main.utils.JsonSerializationService
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.stereotype.Service

@Service
class SimpleCassandraService(
    private val orderInfoRepository: CassandraRepository<OrderInfo, String>,
    private val jsonSerializationService: JsonSerializationService
) {

    /**
     * Сохранить заказ в Cassandra.
     */
    fun saveOrders(order: OrderInfo) {
        orderInfoRepository.save(order)
    }

    /**
     * Найти заказ по ID.
     */
    fun findById(id: String): OrderInfo? {
        return orderInfoRepository.findById(id).orElse(null)
    }

    /**
     * Получить все заказы.
     */
    fun findAll(): List<OrderInfo> {
        return orderInfoRepository.findAll()
    }
}