package main.benchmark.utils

import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.FileOutputStream

@Service
class BenchmarkReportService {
    private val wb = XSSFWorkbook()
    private val sheet = wb.createSheet("Results")
    private val sizeSheet = wb.createSheet("Sizes")

    private var rowIdx = 0
    private var sizeRowIdx = 0

    private val batchSheets = mutableMapOf<Int, Sheet>()
    private val batchRowIdx = mutableMapOf<Int, Int>()

    init {
        sheet.createRow(rowIdx++).apply {
            listOf(
                "strategy", "blockKiB", "chunkKiB", "batch",
                "phase", "µs/op_P95", "tps_P95", "cpu_P95"
            ).forEachIndexed { i, h -> createCell(i).setCellValue(h) }
        }

        sizeSheet.createRow(sizeRowIdx++).apply {
            listOf(
                "blockKiB", "chunkKiB", "batch",
                "simple_MiB", "cassandra_MiB", "precomp_MiB", "appcomp_MiB"
            ).forEachIndexed { i, h -> createCell(i).setCellValue(h) }
        }
    }


    fun writeSizes(blockKiB: Int, chunkKiB: Int, batch: Int, sz: DoubleArray) {
        sizeSheet.createRow(sizeRowIdx++).apply {
            createCell(0).setCellValue(blockKiB.toDouble())
            createCell(1).setCellValue(chunkKiB.toDouble())
            createCell(2).setCellValue(batch.toDouble())
            createCell(3).setCellValue(sz[0] / 1024)
            createCell(4).setCellValue(sz[1] / 1024)
            createCell(5).setCellValue(sz[2] / 1024)
            createCell(6).setCellValue(sz[3] / 1024)
        }
    }

    fun persistMetrics(
        strategy: String,
        blockKiB: Int,
        chunkKiB: Int,
        batch: Int,
        phase: String,
        perOpP95: Double,
        tpsP95: Double,
        cpuP95: Double
    ) {

        writeRow(
            sheet, rowIdx++,
            strategy, blockKiB, chunkKiB, batch,
            phase, perOpP95, tpsP95, cpuP95
        )

        val sh = sheetForBatch(batch)
        val idx = batchRowIdx.getValue(batch)
        writeRow(
            sh, idx,
            strategy, blockKiB, chunkKiB, batch,
            phase, perOpP95, tpsP95, cpuP95
        )
        batchRowIdx[batch] = idx + 1
    }

    fun saveTo(path: String = "benchmark_results.xlsx") {
        FileOutputStream(path).use { wb.write(it) }
        wb.close()
        println("\n✓ Results saved to $path")
    }

    private fun writeRow(
        sh: Sheet,
        rowNum: Int,
        strategy: String,
        blockKiB: Int,
        chunkKiB: Int,
        batch: Int,
        phase: String,
        perOpP95: Double,
        tpsP95: Double,
        cpuP95: Double
    ) = sh.createRow(rowNum).apply {
        createCell(0).setCellValue(strategy)
        createCell(1).setCellValue(blockKiB.toDouble())
        createCell(2).setCellValue(chunkKiB.toDouble())
        createCell(3).setCellValue(batch.toDouble())
        createCell(4).setCellValue(phase)
        createCell(5).setCellValue(perOpP95)
        createCell(6).setCellValue(tpsP95)
        createCell(7).setCellValue(cpuP95)
    }

    private fun sheetForBatch(batch: Int): Sheet =
        batchSheets.getOrPut(batch) {
            val sh = wb.createSheet("batch_$batch")

            val hdr = sh.createRow(0)
            for (i in 0 until sheet.getRow(0).lastCellNum) {
                hdr.createCell(i)
                    .setCellValue(sheet.getRow(0).getCell(i).stringCellValue)
            }
            batchRowIdx[batch] = 1
            sh
        }
}
