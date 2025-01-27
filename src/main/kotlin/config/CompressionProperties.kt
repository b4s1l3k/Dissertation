package main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "compression")
data class CompressionProperties(
    var lz4: Lz4Config = Lz4Config(),
    var snappy: SnappyConfig = SnappyConfig(),
    var zstd: ZstdConfig = ZstdConfig()
)

data class Lz4Config(
    var blockSize: Int = 65536
)

data class SnappyConfig(
    var blockSize: Int = 65536
)

data class ZstdConfig(
    var compressionLevel: Int = 3,
    var enableChecksum: Boolean = true
)