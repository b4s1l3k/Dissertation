package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "benchmark")
data class BenchmarkProperties(
    var ordersCount: Int = 6000,
    var batchSizes: List<Int> = listOf(50, 250, 500),
    var readRatios: List<Double> = listOf(0.25, 0.5, 0.75),
    var chunkSizes: List<Int> = listOf(16, 32, 64, 128, 256),
    var blockSizes: List<Int> = listOf(8 * 1024, 32 * 1024, 64 * 1024, 128 * 1024),
    var bursts: Int = 200,
    var parallelBursts: Int = 10,
    var warmUpBursts: Int = 10,
    var warmUpDelayMs: Long = 10,
    var interBurstDelayMs: Long = 1
)
