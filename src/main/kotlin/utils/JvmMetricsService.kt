package main.utils

import com.sun.management.OperatingSystemMXBean
import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory

@Service
class JvmMetricsService {

    private val osBean = ManagementFactory.getPlatformMXBean(
        OperatingSystemMXBean::class.java
    )

    fun snap(): Pair<Long, Long> =
        System.nanoTime() to osBean.processCpuTime

    fun cpuLoadPct(start: Pair<Long, Long>, end: Pair<Long, Long>): Double {
        val (w0, c0) = start
        val (w1, c1) = end
        val dw = (w1 - w0).coerceAtLeast(1)
        val dc = (c1 - c0).coerceAtLeast(0)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return (dc.toDouble() / dw / cores) * 100.0
    }
}
