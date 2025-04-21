package main.service.generator

import main.data.Payment
import main.data.SimpleOrderInfo
import main.data.UserProfile
import main.service.generator.utils.DataClassGenerator
import org.springframework.stereotype.Service

interface DataGenerationService {
    fun generateUserProfiles(count: Int): List<UserProfile>
    fun generatePayments(count: Int): List<Payment>
    fun generateOrders(count: Int): List<SimpleOrderInfo>
}

@Service
class DataGenerationServiceImpl(
    private val dataGenerator: DataClassGenerator
) : DataGenerationService {

    override fun generateUserProfiles(count: Int): List<UserProfile> =
        List(count) { dataGenerator.generateUserProfile() }

    override fun generatePayments(count: Int): List<Payment> =
        List(count) { dataGenerator.generatePayment() }

    override fun generateOrders(count: Int): List<SimpleOrderInfo> {
        return List(count) { dataGenerator.generateOrderInfo() }
    }
}