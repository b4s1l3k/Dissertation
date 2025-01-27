package main.data

import java.time.Instant
import java.util.*

@JvmInline
value class MetricsId(val value: UUID)

/**
 * Модель данных для метрик системы.
 */
data class Metrics(
    val id: MetricsId = MetricsId(UUID.randomUUID()),
    val timestamp: Long = Instant.now().toEpochMilli(),
    val cpuUsage: Double,
    val memoryUsage: Double,
    val requestCount: Long,
    val errorCount: Long
)
