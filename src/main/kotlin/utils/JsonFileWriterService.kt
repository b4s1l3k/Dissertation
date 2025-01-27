package main.utils

import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Service
import java.io.File

@Service
class JsonFileWriterService<T>(
    private val jsonSerializationService: JsonSerializationService
) {
    /**
     * Запись данных из канала в файл.
     */
    suspend fun writeFromChannelToStream(channel: Channel<T>, filePath: String) {
        File(filePath).outputStream().use { outputStream ->
            jsonSerializationService.objectMapper.factory.createGenerator(outputStream).use { generator ->
                generator.writeStartArray()
                for (item in channel) {
                    generator.writeObject(item)
                }
                generator.writeEndArray()
            }
        }
    }
}