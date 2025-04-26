package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "application")
class ApplicationProperties {
    /**
     * Тип запуска приложения
     * (из application.type)
     */
    var type: ApplicationTypes = ApplicationTypes.preCompressed

    /**
     * Количество заказов
     * (из application.count)
     */
    var count: Int = 60000

    /**
     * Тип бенчмарка
     * (из application.benchmarkType)
     */
    var benchmarkType: BenchmarkType = BenchmarkType.pageSize

    /**
     * application.randomCount
     */
    var randomCount: Boolean = false

    /**
     * application.perTime
     */
    var perTime: Boolean = false
}

enum class ApplicationTypes {
    preCompressed,
    nonCompressed,
    cassandraCompressed,
    onlyPreCompressed,
    benchmark
}

enum class BenchmarkType {
    pageSize,
    parallelism,
    read,
    fallback,
    size
}