package main.service.cassandra

interface CassandraService<T> {
    suspend fun save(entities: List<T>)
    suspend fun findById(id: String): T?
    suspend fun findAll(pageSize: Int = 1000)
    suspend fun deleteAll()
}