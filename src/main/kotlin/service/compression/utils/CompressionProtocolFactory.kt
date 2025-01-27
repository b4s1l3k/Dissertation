package main.service.compression.utils

import main.config.CompressionProperties
import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
import main.service.compression.protocols.Lz4CompressionProtocol
import main.service.compression.protocols.SnappyCompressionProtocol
import main.service.compression.protocols.ZstdCompressionProtocol
import org.springframework.stereotype.Component

interface CompressionProtocolFactory {
    fun getProtocol(type: CompressionType): CompressionProtocol
}

@Component
class CompressionProtocolFactoryImpl(private val properties: CompressionProperties) : CompressionProtocolFactory {

    enum class CompressionType {
        LZ4, SNAPPY, ZSTD
    }

    override fun getProtocol(type: CompressionType): CompressionProtocol {
        return when (type) {
            CompressionType.LZ4 -> Lz4CompressionProtocol(properties.lz4.blockSize)
            CompressionType.SNAPPY -> SnappyCompressionProtocol(properties.snappy.blockSize)
            CompressionType.ZSTD -> ZstdCompressionProtocol(
                properties.zstd.compressionLevel,
                properties.zstd.enableChecksum
            )
        }
    }
}