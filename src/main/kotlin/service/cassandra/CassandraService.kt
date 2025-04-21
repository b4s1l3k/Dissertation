package main.service.cassandra

import main.data.OrderInfo

interface CassandraService {
    suspend fun save(entities: List<OrderInfo>)
    suspend fun findById(id: String): OrderInfo?
    suspend fun findAll(pageSize: Int = 1000)
    suspend fun deleteAll()
}