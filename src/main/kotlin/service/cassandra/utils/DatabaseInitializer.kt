package main.service.cassandra.utils

import org.springframework.stereotype.Service

@Service
class DatabaseInitializer(
    private val cassandraSchemaService: CassandraSchemaService
) {

    fun initializeSchema() {
        cassandraSchemaService.createKeyspace("dissertation")

        val createOrderInfoTableCql = """
            CREATE TABLE IF NOT EXISTS dissertation.order_info (
                id UUID PRIMARY KEY,
                user_id UUID,
                product_id UUID,
                amount DECIMAL,
                created_at TIMESTAMP
            )
        """
        cassandraSchemaService.createTable(createOrderInfoTableCql)
    }
}