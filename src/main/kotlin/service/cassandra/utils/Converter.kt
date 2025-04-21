import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.nio.ByteBuffer

@WritingConverter
class ByteArrayToBlobConverter : Converter<ByteArray, ByteBuffer> {
    override fun convert(source: ByteArray): ByteBuffer =
        ByteBuffer.wrap(source)
}

@ReadingConverter
class BlobToByteArrayConverter : Converter<ByteBuffer, ByteArray> {
    override fun convert(source: ByteBuffer): ByteArray {
        return if (source.hasArray()) {
            source.array()
        } else {
            val bytes = ByteArray(source.remaining())
            source.get(bytes)
            bytes
        }
    }
}