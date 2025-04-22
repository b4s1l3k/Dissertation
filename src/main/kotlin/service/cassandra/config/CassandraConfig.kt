package main.service.cassandra.config

import BlobToByteArrayConverter
import ByteArrayToBlobConverter
import main.config.CassandraProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.core.convert.CassandraCustomConversions
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories

@Configuration
@EnableCassandraRepositories(basePackages = ["main"])
class CassandraConfig(
    private val props: CassandraProperties
) : AbstractCassandraConfiguration() {

    override fun getKeyspaceName() = props.keyspace
    override fun getContactPoints() = props.contactPoints
    override fun getPort() = props.port
    override fun getLocalDataCenter() = props.localDatacenter
    override fun getSchemaAction() = SchemaAction.CREATE_IF_NOT_EXISTS
    override fun getEntityBasePackages() = arrayOf("main.data")

    @Bean
    override fun customConversions(): CassandraCustomConversions =
        CassandraCustomConversions(
            listOf(
                ByteArrayToBlobConverter(),
                BlobToByteArrayConverter()
            )
        )
}
