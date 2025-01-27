package main.service.compression.protocols

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import main.service.compression.utils.CompressionProtocol
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream

@Component
class ZstdCompressionProtocol(
    @Value("\${compression.zstd.compressionLevel}") private val compressionLevel: Int,
    @Value("\${compression.zstd.enableChecksum}") private val enableChecksum: Boolean
) : CompressionProtocol {
    override fun compress(input: InputStream, output: OutputStream) {
        ZstdOutputStream(output, compressionLevel).apply {
            if (enableChecksum) enableChecksum
        }.use { zstdOutput ->
            input.copyTo(zstdOutput)
        }
    }

    override fun decompress(input: InputStream, output: OutputStream) {
        ZstdInputStream(input).use { zstdInput ->
            zstdInput.copyTo(output)
        }
    }
}