package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "compression")
class CompressionProperties {
    /**
     * Настройки Snappy
     * (compression.snappy.blockSize)
     */
    var snappy: Snappy = Snappy()

    /**
     * Настройки LZ4
     * (compression.lz4.blockSize)
     */
    var lz4: Lz4 = Lz4()

    /**
     * Настройки Zstd
     * (compression.zstd.compressionLevel, compression.zstd.enableChecksum)
     */
    var zstd: Zstd = Zstd()
}

data class Snappy(
    /** Размер буфера для Snappy */
    var blockSize: Int = 65536
)

data class Lz4(
    /** Размер буфера для LZ4 */
    var blockSize: Int = 262144
)

data class Zstd(
    /** Уровень сжатия Zstd (1–22) */
    var compressionLevel: Int = 1,
    /** Включать контрольную сумму в фрейме */
    var enableChecksum: Boolean = false
)
