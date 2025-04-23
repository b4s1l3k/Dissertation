package main.utils

import com.sun.management.OperatingSystemMXBean
import org.springframework.stereotype.Service
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryPoolMXBean
import java.util.concurrent.atomic.AtomicLong

@Service
class JvmMetricsService {
    private val osBean = ManagementFactory.getPlatformMXBean(
        OperatingSystemMXBean::class.java
    )
    private val memBean = ManagementFactory.getMemoryMXBean()
    private val poolBeans: List<MemoryPoolMXBean> =
        ManagementFactory.getMemoryPoolMXBeans()
    private val bufferPools: List<BufferPoolMXBean> =
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java)

    private val lastWall = AtomicLong(System.nanoTime())
    private val lastCpu = AtomicLong(osBean.processCpuTime)

    data class Metrics(
        val cpuPct: Double,
        val heapMiB: Double,
        val nonHeapMiB: Double,
        val directBufferMiB: Double,
        val poolsMiB: Double,
        val totalUsedMiB: Double,
        val gcPauseMs: Long
    )

    @Synchronized
    fun sample(): Metrics {
        val nowWall = System.nanoTime()
        val nowCpu = osBean.processCpuTime
        val deltaW = (nowWall - lastWall.getAndSet(nowWall)).coerceAtLeast(1)
        val deltaC = (nowCpu - lastCpu.getAndSet(nowCpu)).coerceAtLeast(0)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuPct = (deltaC / (deltaW * cores) * 100.0).coerceIn(0.0, 100.0)

        val heapUsed = memBean.heapMemoryUsage.used.toDouble()
        val nonHeapUsed = memBean.nonHeapMemoryUsage.used.toDouble()

        val poolsUsed = poolBeans
            .map { it.usage.used.toDouble() }
            .sum()

        val directUsed = bufferPools
            .map { it.memoryUsed.toDouble() }
            .sum()

        val gcPause = ManagementFactory.getGarbageCollectorMXBeans()
            .sumOf { it.collectionTime }

        val toMiB = 1.0 / (1024.0 * 1024.0)
        val heapMiB = heapUsed * toMiB
        val nonHeapMiB = nonHeapUsed * toMiB
        val poolsMiB = poolsUsed * toMiB
        val directMiB = directUsed * toMiB
        val totalMiB = heapMiB + nonHeapMiB

        return Metrics(
            cpuPct = cpuPct,
            heapMiB = heapMiB,
            nonHeapMiB = nonHeapMiB,
            directBufferMiB = directMiB,
            poolsMiB = poolsMiB,
            totalUsedMiB = totalMiB,
            gcPauseMs = gcPause
        )
    }
}
