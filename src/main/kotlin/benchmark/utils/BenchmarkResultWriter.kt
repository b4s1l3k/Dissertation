package main.benchmark.utils

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream

class BenchmarkResultWriter(
    private val fileName: String = "benchmark_results.xlsx"
) : AutoCloseable {

    private val workbook = XSSFWorkbook()

    private val sheetMain = workbook.createSheet("Results")
    private var rowIdxMain = 1

    private val batchSheets   = mutableMapOf<Int, org.apache.poi.ss.usermodel.Sheet>()
    private val batchRowIndex = mutableMapOf<Int, Int>()

    private val header = listOf(
        "strategy","blockB","chunkKiB","batch","readRatio",
        "ms/op_P95","tps_P95","cpu_P95","mem_P95_MiB",
        "simple_KiB","cassandra_KiB","precomp_KiB","appcomp_KiB"
    ).also { h ->
        val r = sheetMain.createRow(0)
        h.forEachIndexed { i, cell -> r.createCell(i).setCellValue(cell) }
    }

    @Synchronized
    fun writeRow(
        strategy: String,
        blockB: Int,
        chunkKiB: Int,
        batch: Int,
        readRatio: Double,
        perOp: Double,
        tps: Double,
        cpu: Double,
        mem: Double,
        sizeRow: DoubleArray
    ) {
        sheetMain.createRow(rowIdxMain++).fill(
            strategy, blockB, chunkKiB, batch, readRatio,
            perOp, tps, cpu, mem, sizeRow
        )

        val sh   = batchSheets.getOrPut(batch) { createBatchSheet(batch) }
        val idxB = batchRowIndex.getValue(batch)
        sh.createRow(idxB).fill(
            strategy, blockB, chunkKiB, batch, readRatio,
            perOp, tps, cpu, mem, sizeRow
        )
        batchRowIndex[batch] = idxB + 1
    }

    @Synchronized
    override fun close() {
        FileOutputStream(fileName).use { workbook.write(it) }
        workbook.close()
    }

    private fun createBatchSheet(batch: Int): org.apache.poi.ss.usermodel.Sheet {
        val sh  = workbook.createSheet("batch_$batch")
        val hdr = sh.createRow(0)
        header.forEachIndexed { i, cell -> hdr.createCell(i).setCellValue(cell) }
        batchRowIndex[batch] = 1
        return sh
    }

    private fun org.apache.poi.ss.usermodel.Row.fill(
        strategy: String,
        blockB: Int,
        chunkKiB: Int,
        batch: Int,
        readRatio: Double,
        perOp: Double,
        tps: Double,
        cpu: Double,
        mem: Double,
        sz: DoubleArray
    ) {
        createCell(0).setCellValue(strategy)
        createCell(1).setCellValue(blockB.toDouble())
        createCell(2).setCellValue(chunkKiB.toDouble())
        createCell(3).setCellValue(batch.toDouble())
        createCell(4).setCellValue(readRatio)
        createCell(5).setCellValue(perOp)
        createCell(6).setCellValue(tps)
        createCell(7).setCellValue(cpu)
        createCell(8).setCellValue(mem)
        createCell(9).setCellValue(sz[0])
        createCell(10).setCellValue(sz[1])
        createCell(11).setCellValue(sz[2])
        createCell(12).setCellValue(sz[3])
    }
}
