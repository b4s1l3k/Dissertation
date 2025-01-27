package main.service.cassandra.utils

import com.datastax.oss.driver.api.core.CqlIdentifier
import org.springframework.data.cassandra.core.CassandraAdminOperations
import org.springframework.data.cassandra.core.cql.CqlTemplate
import org.springframework.stereotype.Service

@Service
class CassandraSchemaService(
    private val cqlTemplate: CqlTemplate
) {

    /**
     * Создание ключевого пространства.
     */
    fun createKeyspace(keyspace: String, replicationStrategy: String = "{'class': 'SimpleStrategy', 'replication_factor': 1}") {
        val createKeyspaceCql = """
            CREATE KEYSPACE IF NOT EXISTS $keyspace
            WITH replication = $replicationStrategy
        """
        cqlTemplate.execute(createKeyspaceCql)
    }

    /**
     * Создание таблицы, если она не существует.
     */
    fun createTable(createTableCql: String) {
        cqlTemplate.execute(createTableCql)
    }
}