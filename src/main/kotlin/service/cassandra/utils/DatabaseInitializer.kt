package main.service.cassandra.utils

import jakarta.annotation.PostConstruct
import org.springframework.data.cassandra.core.cql.CqlTemplate
import org.springframework.stereotype.Service
import java.lang.Thread.sleep

@Service
class DatabaseInitializer(
    private val cql: CqlTemplate
) {
    @PostConstruct
    fun initializeSchema() {
        cql.execute(
            """
            CREATE TYPE IF NOT EXISTS dissertation.money_type (
                money_amount   decimal,
                currency       text
            );
        """.trimIndent()
        )

        cql.execute(
            """
            CREATE TYPE IF NOT EXISTS dissertation.product_type (
                id             uuid,
                name           text,
                description    text,
                price          frozen<money_type>,
                stock          int
            );
        """.trimIndent()
        )

        cql.execute(
            """
            CREATE TYPE IF NOT EXISTS dissertation.user_profile_type (
                id                text,
                name              text,
                email             text,
                phone             text,
                address           text,
                birth_date        bigint,
                gender            text,
                created_at        bigint,
                last_updated_at   bigint,
                preferences       map<text, text>
            );
        """.trimIndent()
        )

        cql.execute(
            """
            CREATE TYPE IF NOT EXISTS dissertation.payment_type (
                id                  uuid,
                amount              frozen<money_type>,
                status              text,
                method              text,
                description         text,
                recipient_name      text,
                recipient_account   bigint,
                sender_name         text,
                sender_account      bigint,
                transaction_fee     frozen<money_type>,
                tax_amount          frozen<money_type>,
                metadata            map<text, text>,
                invoice_number      bigint,
                confirmation_code   text,
                scheduled_date      bigint,
                expiration_date     bigint
            );
        """.trimIndent()
        )

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.simple_order_info (
                id                          text PRIMARY KEY,
                user                        frozen<user_profile_type>,
                payment                     frozen<payment_type>,
                product                     list<frozen<product_type>>,
                quantity                    int,
                total_price                 frozen<money_type>,
                order_date                  bigint,
                status                      text,
                delivery_method             text,
                delivery_address            text,
                estimated_delivery_date     bigint,
                tracking_number             text,
                payment_id                  uuid,
                discount_amount             frozen<money_type>,
                tax_amount                  frozen<money_type>,
                metadata                    map<text, text>
            )
            WITH compression = {'enabled': false}
            AND crc_check_chance = 0.1;
        """.trimIndent()
        )

        cql.execute(
            """
            ALTER TABLE dissertation.simple_order_info
            WITH compression = {'enabled': false};
        """.trimIndent()
        )

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.cassandra_order_info (
                id                          text PRIMARY KEY,
                user                        frozen<user_profile_type>,
                payment                     frozen<payment_type>,
                product                     list<frozen<product_type>>,
                quantity                    int,
                total_price                 frozen<money_type>,
                order_date                  bigint,
                status                      text,
                delivery_method             text,
                delivery_address            text,
                estimated_delivery_date     bigint,
                tracking_number             text,
                payment_id                  uuid,
                discount_amount             frozen<money_type>,
                tax_amount                  frozen<money_type>,
                metadata                    map<text, text>
            )
            WITH compression = {
                'class': 'org.apache.cassandra.io.compress.DeflateCompressor',
                'chunk_length_in_kb': '4'
            }
            AND crc_check_chance = 0.1;
        """.trimIndent()
        )

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.precompressed_order_info (
                id                  text PRIMARY KEY,
                compressed_payload  blob
            )
            WITH compression = {
                'class': 'org.apache.cassandra.io.compress.DeflateCompressor',
                'chunk_length_in_kb': '4'
            }
            AND crc_check_chance = 0.1;
        """.trimIndent()
        )

        cql.execute(
            """
            CREATE TABLE IF NOT EXISTS dissertation.app_compressed_order_info (
                id                  text PRIMARY KEY,
                compressed_payload  blob
            )
            WITH compression = {'enabled': false}
            AND crc_check_chance = 0.1;
        """.trimIndent()
        )

        cql.execute(
            """
            ALTER TABLE dissertation.app_compressed_order_info
            WITH compression = {'enabled': false};
        """.trimIndent()
        )
    }

    private fun <T> retrySync(
        attempts: Int = 10,
        initialDelayMs: Long = 100,
        factor: Double = 2.0,
        block: () -> T
    ): T {
        var delay = initialDelayMs
        var lastEx: Exception? = null
        for (i in 1..attempts) {
            try {
                return block()
            } catch (ex: Exception) {
                lastEx = ex
                println("DatabaseInitializer: попытка $i/$attempts упала: ${ex.message}")
                if (i == attempts) break
                sleep(delay)
                delay = (delay * factor).toLong()
            }
        }
        println("DatabaseInitializer: все $attempts попыток неуспешны, выбрасываю последнее исключение")
        throw lastEx!!
    }
}