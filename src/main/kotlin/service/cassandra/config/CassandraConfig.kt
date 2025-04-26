package main.service.cassandra.config

import BlobToByteArrayConverter
import ByteArrayToBlobConverter
import com.datastax.oss.driver.api.core.CqlSessionBuilder
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.config.DriverConfigLoader
import org.springframework.boot.autoconfigure.cassandra.DriverConfigLoaderBuilderCustomizer
import main.config.CassandraProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import org.springframework.data.cassandra.core.convert.CassandraCustomConversions
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories
import java.time.Duration

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

    @Bean
    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
        SessionBuilderConfigurer { builder: CqlSessionBuilder ->
            val loader = DriverConfigLoader.programmaticBuilder()
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(props.timeout))
                .build()

            builder.withConfigLoader(loader)
        }
}
