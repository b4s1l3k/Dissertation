package main.service.compression

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.xerial.snappy.SnappyInputStream
import org.xerial.snappy.SnappyOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

interface CompressionService {
    fun compressData(data: ByteArray): ByteArray
    fun decompressData(data: ByteArray): ByteArray
}

@Component
class SnappyCompressionProtocol(@Value("\${compression.snappy.blockSize}") var blockSize: Int) :
    CompressionService {
    override fun compressData(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ByteArrayInputStream(data).use { input ->
            SnappyOutputStream(output).use { snappyOut ->
                input.copyTo(snappyOut, blockSize)
            }
        }
        return output.toByteArray()
    }

    override fun decompressData(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        SnappyInputStream(ByteArrayInputStream(data)).use { snappyIn ->
            snappyIn.copyTo(output, blockSize)
        }
        return output.toByteArray()
    }
}