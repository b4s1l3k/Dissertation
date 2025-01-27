package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "generating")
data class GeneratingProperties(
    var userProfiles: UserProfiles = UserProfiles(),
    var payments: Payments = Payments(),
    var orders: Orders = Orders()
)

data class UserProfiles(
    var count: Int = 1000
)

data class Payments(
    var count: Int = 1000
)

data class Orders(
    var count: Int = 1000
)