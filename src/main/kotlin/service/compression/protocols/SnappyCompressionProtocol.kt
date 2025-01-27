package main.service.compression.protocols

import main.service.compression.utils.CompressionProtocol
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.xerial.snappy.SnappyInputStream
import org.xerial.snappy.SnappyOutputStream
import java.io.InputStream
import java.io.OutputStream

@Component
class SnappyCompressionProtocol(@Value("\${compression.snappy.blockSize}") private val blockSize: Int) :
    CompressionProtocol {
    override fun compress(input: InputStream, output: OutputStream) {
        SnappyOutputStream(output).use { snappyOutput ->
            val buffer = ByteArray(blockSize)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                snappyOutput.write(buffer, 0, bytesRead)
            }
        }
    }

    override fun decompress(input: InputStream, output: OutputStream) {
        SnappyInputStream(input).use { snappyInput ->
            val buffer = ByteArray(blockSize)
            var bytesRead: Int
            while (snappyInput.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}