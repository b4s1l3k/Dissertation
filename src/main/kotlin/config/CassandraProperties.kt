package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "spring.data.cassandra")
data class CassandraProperties(
    var contactPoints: String = "localhost",
    var port: Int = 9042,
    var keyspace: String = "dissertation",
    var username: String = "cassandra",
    var password: String = "cassandra",
    var jmxHost: String = "localhost",
    var jmxPort: Int = 7199,
    var localDatacenter: String = "datacenter1"
)