package main.service.generator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import main.data.OrderInfo
import main.data.Payment
import main.data.UserProfile
import main.service.generator.utils.DataClassGenerator
import main.utils.JsonFileWriterService
import org.springframework.stereotype.Service

interface DataGenerationService {
    suspend fun generateAndSaveUserProfilesToFile(count: Int, filePath: String)
    suspend fun generateAndSavePaymentsToFile(count: Int, filePath: String)
    suspend fun generateAndSaveOrdersToFile(count: Int, filePath: String)
}

@Service
class DataGenerationServiceImpl (
    private val dataGenerator: DataClassGenerator,
    private val userProfileWriterService: JsonFileWriterService<UserProfile>,
    private val paymentWriterService: JsonFileWriterService<Payment>,
    private val orderInfoWriterService: JsonFileWriterService<OrderInfo>
) : DataGenerationService {
    /**
     * Асинхронная генерация и сохранение профилей пользователей.
     */
    override suspend fun generateAndSaveUserProfilesToFile(count: Int, filePath: String) {
        coroutineScope {
            val channel = Channel<UserProfile>(Channel.UNLIMITED)
            val writerJob = launch(Dispatchers.IO) {
                userProfileWriterService.writeFromChannelToStream(channel, filePath)
            }

            launchGenerator(channel, count) { dataGenerator.generateUserProfile() }

            writerJob.join()
        }
    }

    /**
     * Асинхронная генерация и сохранение платежей.
     */
    override suspend fun generateAndSavePaymentsToFile(count: Int, filePath: String) {
        coroutineScope {
            val channel = Channel<Payment>(Channel.UNLIMITED)
            val writerJob = launch(Dispatchers.IO) {
                paymentWriterService.writeFromChannelToStream(channel, filePath)
            }

            launchGenerator(channel, count) { dataGenerator.generatePayment() }

            writerJob.join()
        }
    }

    /**
     * Асинхронная генерация и сохранение заказов.
     */
    override suspend fun generateAndSaveOrdersToFile(count: Int, filePath: String) {
        coroutineScope {
            val channel = Channel<OrderInfo>(Channel.UNLIMITED)
            val writerJob = launch(Dispatchers.IO) {
                orderInfoWriterService.writeFromChannelToStream(channel, filePath)
            }

            launchGenerator(channel, count) { dataGenerator.generateOrderInfo() }

            writerJob.join()
        }
    }

    /**
     * Универсальный метод запуска генерации данных.
     */
    private fun <T> CoroutineScope.launchGenerator(channel: Channel<T>, count: Int, generator: () -> T) =
        launch(Dispatchers.Default) {
            try {
                repeat(count) {
                    channel.send(generator())
                }
            } finally {
                channel.close()
            }
        }
}