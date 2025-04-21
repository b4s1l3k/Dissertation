package main.service.cassandra.utils

import jakarta.annotation.PostConstruct
import org.springframework.data.cassandra.core.cql.CqlTemplate
import org.springframework.stereotype.Service

@Service
class DatabaseInitializer(
    private val cql: CqlTemplate
) {
    @PostConstruct
    fun initializeSchema() {

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.cassandra_order_info (
                id text PRIMARY KEY,
                user text,
                payment text,
                product text,
                quantity int,
                totalPrice text,
                orderDate bigint,
                status text,
                deliveryMethod text,
                deliveryAddress text,
                estimatedDeliveryDate bigint,
                trackingNumber text,
                paymentId text,
                discountAmount text,
                taxAmount text,
                metadata map<text, text>
            )
            WITH compression = {
                'class'             : 'org.apache.cassandra.io.compress.ZstdCompressor',
                'chunk_length_in_kb': '256',
                'compression_level' : '22'
            }
            AND crc_check_chance = 0.1
            """.trimIndent()
        )

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.compressed_order_info (
                id text PRIMARY KEY,
                compressed_payload blob
            )
            WITH compression = {
                'class'             : 'org.apache.cassandra.io.compress.ZstdCompressor',
                'chunk_length_in_kb': '256',
                'compression_level' : '22'
            }
            AND crc_check_chance = 0.1
            """.trimIndent()
        )

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.simple_order_info (
                id text PRIMARY KEY,
                user text,
                payment text,
                product text,
                quantity int,
                totalPrice text,
                orderDate bigint,
                status text,
                deliveryMethod text,
                deliveryAddress text,
                estimatedDeliveryDate bigint,
                trackingNumber text,
                paymentId text,
                discountAmount text,
                taxAmount text,
                metadata map<text, text>
            )
            WITH compression = {'enabled':'false'} 
            AND crc_check_chance = 0.1
            """.trimIndent()
        )

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.precompressed_order_info (
                id text PRIMARY KEY,
                compressed_payload blob
            )
            WITH compression = {'enabled':'false'}
            AND crc_check_chance = 0.1
            """.trimIndent()
        )
    }
}