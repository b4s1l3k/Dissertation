package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "generating")
data class GeneratingProperties(
    var perCall: Int = 100
)