package main.utils

import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean

@Service
class JvmMetricsService {

    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val osBean: OperatingSystemMXBean by lazy {
        ManagementFactory
            .getPlatformMXBean(OperatingSystemMXBean::class.java)
    }

    /**
     * Печатает раз в интервал:
     *  - процессорную нагрузку JVM‑процесса (%)
     *  - используемый heap (MiB)
     */
    @Scheduled(fixedRateString = "1000")
    fun report() {
        val now       = LocalDateTime.now().format(dtf)
        val cpu       = osBean.processCpuLoad.takeIf { it >= 0 }?.times(100) ?: Double.NaN
        val runtime   = Runtime.getRuntime()
        val usedHeap  = (runtime.totalMemory() - runtime.freeMemory()).toDouble() / (1024 * 1024)

        println("[$now] JVM CPU: ${"%.1f".format(cpu)}%, Used heap: ${"%.1f".format(usedHeap)} MiB")
    }
}
