package main.service.report

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
    private val batchRowIndex = mutableMapOf<Int, Int>()

    init {
        sheet.createRow(rowIdx++).apply {
            listOf(
                "strategy", "blockKiB", "chunkKiB", "batch", "readRatio",
                "phase", "ms/op_P95", "tps_P95", "cpu_P95",
                "simple_MiB", "cassandra_MiB", "precomp_MiB", "appcomp_MiB"
            ).forEachIndexed { i, h -> createCell(i).setCellValue(h) }
        }

        sizeSheet.createRow(sizeRowIdx++).apply {
            listOf("blockKiB", "chunkKiB",
                "simple_MiB", "cassandra_MiB",
                "precomp_MiB", "appcomp_MiB"
            ).forEachIndexed { i, h -> createCell(i).setCellValue(h) }
        }
    }

    fun writeSizes(blockKiB: Int, chunkKiB: Int, sz: DoubleArray) {
        sizeSheet.createRow(sizeRowIdx++).apply {
            createCell(0).setCellValue(blockKiB.toDouble())
            createCell(1).setCellValue(chunkKiB.toDouble())
            createCell(2).setCellValue(sz[0] / 1024)
            createCell(3).setCellValue(sz[1] / 1024)
            createCell(4).setCellValue(sz[2] / 1024)
            createCell(5).setCellValue(sz[3] / 1024)
        }
    }

    fun persistMetrics(
        strategy: String,
        blockKiB: Int,
        chunkKiB: Int,
        batch: Int,
        readRatio: Double,
        phase: String,
        perOpP95: Double,
        tpsP95: Double,
        cpuP95: Double,
        sizeRow: DoubleArray
    ) {
        writeRow(sheet, rowIdx++, strategy, blockKiB, chunkKiB, batch, readRatio,
            phase, perOpP95, tpsP95, cpuP95, sizeRow)

        val sh = sheetForBatch(batch)
        val idx = batchRowIndex.getValue(batch)
        writeRow(sh, idx, strategy, blockKiB, chunkKiB, batch, readRatio,
            phase, perOpP95, tpsP95, cpuP95, sizeRow)
        batchRowIndex[batch] = idx + 1
    }

    fun saveTo(path: String = "benchmark_results.xlsx") {
        FileOutputStream(path).use { wb.write(it) }
        wb.close()
    }

    private fun writeRow(
        sh: Sheet, rowNum: Int,
        strategy: String,
        blockKiB: Int,
        chunkKiB: Int,
        batch: Int,
        readRatio: Double,
        phase: String,
        perOpP95: Double,
        tpsP95: Double,
        cpuP95: Double,
        sizeRow: DoubleArray
    ) = sh.createRow(rowNum).apply {
        createCell(0).setCellValue(strategy)
        createCell(1).setCellValue(blockKiB.toDouble())
        createCell(2).setCellValue(chunkKiB.toDouble())
        createCell(3).setCellValue(batch.toDouble())
        createCell(4).setCellValue(readRatio)
        createCell(5).setCellValue(phase)
        createCell(6).setCellValue(perOpP95)
        createCell(7).setCellValue(tpsP95)
        createCell(8).setCellValue(cpuP95)
        createCell(9).setCellValue(sizeRow[0] / 1024)
        createCell(10).setCellValue(sizeRow[1] / 1024)
        createCell(11).setCellValue(sizeRow[2] / 1024)
        createCell(12).setCellValue(sizeRow[3] / 1024)
    }

    private fun sheetForBatch(batch: Int): Sheet =
        batchSheets.getOrPut(batch) {
            val sh = wb.createSheet("batch_$batch")
            val hdr = sh.createRow(0)
            for (i in 0 until sheet.getRow(0).lastCellNum) {
                hdr.createCell(i)
                    .setCellValue(sheet.getRow(0).getCell(i).stringCellValue)
            }
            batchRowIndex[batch] = 1
            sh
        }
}