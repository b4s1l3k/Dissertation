//package main.service.cassandra
//
//import main.data.CompressedOrderInfo
//import main.data.OrderInfo
//import main.service.compression.CompressionService
//import main.service.compression.utils.CompressionProtocolFactoryImpl.CompressionType
//import main.utils.JsonSerializationService
//import org.springframework.data.cassandra.repository.CassandraRepository
//import org.springframework.stereotype.Service
//
//@Service
//class CompressedCassandraService(
//    private val compressedOrderInfoRepository: CassandraRepository<CompressedOrderInfo, String>,
//    private val compressionService: CompressionService,
//    private val jsonSerializationService: JsonSerializationService
//) {
//    /**
//     * Сохранить сжатый объект в базу данных.
//     */
//    fun saveCompressed(entity: OrderInfo, protocol: CompressionType): CompressedOrderInfo {
//        val jsonData = jsonSerializationService.serializeToBytes(entity)
//        val compressedPayload = compressionService.compressData(jsonData, protocol)
//
//        val compressedData = CompressedOrderInfo(
//            id = entity.id,
//            protocol = protocol,
//            compressedPayload = compressedPayload
//        )
//        return compressedOrderInfoRepository.save(compressedData)
//    }
//
//    /**
//     * Найти запись по ID.
//     */
//    fun findById(id: String): CompressedOrderInfo? {
//        return compressedOrderInfoRepository.findById(id).orElse(null)
//    }
//
//    /**
//     * Получить все записи.
//     */
//    fun findAll(): List<CompressedOrderInfo> {
//        return compressedOrderInfoRepository.findAll()
//    }
//
//    /**
//     * Распаковать объект `CompressedOrderInfo` в `OrderInfo`.
//     */
//    fun decompress(compressedOrderInfo: CompressedOrderInfo): OrderInfo {
//        val decompressedData = compressionService.decompressData(
//            compressedOrderInfo.compressedPayload,
//            compressedOrderInfo.protocol
//        )
//        return jsonSerializationService.deserializeFromBytes(decompressedData, OrderInfo::class.java)
//    }
//}