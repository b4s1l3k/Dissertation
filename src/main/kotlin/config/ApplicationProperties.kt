package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "application")
data class ApplicationProperties(
    var applicationType: ApplicationType = ApplicationType()
)

data class ApplicationType(
    var type: ApplicationTypes = ApplicationTypes.generatingPlusCompression
)

enum class ApplicationTypes {
    cassandra,
    generating,
    compression,
    generatingPlusCompression
}