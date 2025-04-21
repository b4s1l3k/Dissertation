package main.data

import java.time.Instant
import java.util.*


/**
 * Модель данных для метрик системы.
 */
data class Metrics(
    val id: UUID = UUID.randomUUID(),
    val timestamp: Long = Instant.now().toEpochMilli(),
    val cpuUsage: Double,
    val memoryUsage: Double,
    val requestCount: Long,
    val errorCount: Long
)
