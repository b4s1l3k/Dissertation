package main.service.compression.protocols

import main.service.compression.utils.CompressionProtocol
import net.jpountz.lz4.LZ4BlockInputStream
import net.jpountz.lz4.LZ4BlockOutputStream
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream

@Component
class Lz4CompressionProtocol(
    @Value("\${compression.lz4.blockSize}") private val blockSize: Int
) : CompressionProtocol {

    override fun compress(input: InputStream, output: OutputStream) {
        LZ4BlockOutputStream(output, blockSize).use { lz4Output ->
            input.copyTo(lz4Output)
        }
    }

    override fun decompress(input: InputStream, output: OutputStream) {
        LZ4BlockInputStream(input).use { lz4Input ->
            lz4Input.copyTo(output)
        }
    }
}