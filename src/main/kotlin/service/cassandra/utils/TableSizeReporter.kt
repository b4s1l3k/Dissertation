package main.service.cassandra.utils

import main.config.CassandraProperties
import org.springframework.data.cassandra.core.cql.CqlTemplate
import org.springframework.stereotype.Service
import javax.management.ObjectName
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

@Service
class TableSizeReporter(
    private val cql: CqlTemplate,
    private val cassProps: CassandraProperties
) {

    fun fetchTableSizes(
        vararg tables: String,
        compact: Boolean = true
    ): Map<String, Long> {
        val ks = cassProps.keyspace
        val url = JMXServiceURL(
            "service:jmx:rmi:///jndi/rmi://${cassProps.jmxHost}:${cassProps.jmxPort}/jmxrmi"
        )
        JMXConnectorFactory.connect(url).use { jmxc ->
            val mbs = jmxc.mBeanServerConnection
//            val raw = jmxc.mBeanServerConnection
//            val mbs = Jmxс.wrap(raw)

            invokeStorageService(
                mbs, "forceKeyspaceFlush", arrayOf(ks, arrayOf<String>()), arrayOf(
                    "java.lang.String", "[Ljava.lang.String;"
                )
            )

            if (compact) {
                invokeStorageService(
                    mbs, "forceKeyspaceCompaction", arrayOf(true, ks, tables), arrayOf(
                        "boolean", "java.lang.String", "[Ljava.lang.String;"
                    )
                )
                invokeStorageService(
                    mbs, "forceKeyspaceFlush", arrayOf(ks, arrayOf<String>()), arrayOf(
                        "java.lang.String", "[Ljava.lang.String;"
                    )
                )
            }

            println("✅ [TableSizeReporter] Measurement complete.\n")
            return tables.associateWith { tbl ->
                getFromMetricsJmx(mbs, ks, tbl)
                    ?: getFromLegacyJmx(mbs, ks, tbl)
                    ?: getFromSstablesView(tbl)
                    ?: estimateFromSizeEstimates(tbl)
            }
        }
    }

    fun reportTableSizes(vararg tables: String) {
        val ks = cassProps.keyspace
        println("\n=== Оценка размера таблиц в keyspace '$ks' ===")

        val url = JMXServiceURL(
            "service:jmx:rmi:///jndi/rmi://${cassProps.jmxHost}:${cassProps.jmxPort}/jmxrmi"
        )
        JMXConnectorFactory.connect(url).use { jmxc ->
//            val raw = jmxc.mBeanServerConnection
//            val mbs = Jmxс.wrap(raw)

            val mbs = jmxc.mBeanServerConnection

            flushKeyspace(mbs, ks)

            tables.forEach { tbl ->
                val bytes = getFromMetricsJmx(mbs, ks, tbl)
                    ?: getFromLegacyJmx(mbs, ks, tbl)
                    ?: getFromSstablesView(tbl)
                    ?: estimateFromSizeEstimates(tbl)

                println("${tbl.padEnd(25)} : $bytes байт (~${bytes / 1024} KiB)")
            }
        }
    }

    private fun flushKeyspace(mbs: javax.management.MBeanServerConnection, keyspace: String) {
        val name = ObjectName("org.apache.cassandra.db:type=StorageService")
        val params = arrayOf<Any>(keyspace, arrayOf<String>())
        val sig = arrayOf("java.lang.String", "[Ljava.lang.String;")
        mbs.invoke(name, "forceKeyspaceFlush", params, sig)
        println("✓ Flush keyspace '$keyspace' через JMX выполнен\n")
    }

    private fun invokeStorageService(
        mbs: javax.management.MBeanServerConnection,
        operation: String,
        params: Array<Any>,
        signature: Array<String>
    ) {
        val name = ObjectName("org.apache.cassandra.db:type=StorageService")
        mbs.invoke(name, operation, params, signature)
    }

    private fun getFromMetricsJmx(
        mbs: javax.management.MBeanServerConnection,
        keyspace: String,
        table: String
    ): Long? {
        val base = "org.apache.cassandra.metrics:type=Table,keyspace=$keyspace,scope=$table,name="
        return listOf("TotalDiskSpaceUsed", "LiveDiskSpaceUsed")
            .asSequence()
            .mapNotNull { metric ->
                runCatching {
                    val mbean = ObjectName(base + metric)
                    (mbs.getAttribute(mbean, "Count") as Number).toLong().takeIf { it > 0 }
                }.getOrNull()
            }
            .firstOrNull()
    }

    private fun getFromLegacyJmx(
        mbs: javax.management.MBeanServerConnection,
        keyspace: String,
        table: String
    ): Long? = runCatching {
        val name = ObjectName(
            "org.apache.cassandra.db:type=ColumnFamilies,keyspace=$keyspace,columnfamily=$table"
        )
        (mbs.getAttribute(name, "LiveDiskSpaceUsed") as Number).toLong().takeIf { it > 0 }
    }.getOrNull()

    private fun getFromSstablesView(table: String): Long? = runCatching {
        val sql = """
            SELECT sum(space_used_bytes) AS total_bytes
              FROM system_views.sstables
             WHERE keyspace_name=? AND table_name=? ALLOW FILTERING
        """.trimIndent()
        (cql.queryForList(sql, cassProps.keyspace, table)
            .firstOrNull()?.get("total_bytes") as? Number)
            ?.toLong()
            ?.takeIf { it > 0 }
    }.getOrNull()

    private fun estimateFromSizeEstimates(table: String): Long {
        val sql = """
            SELECT mean_partition_size, partitions_count
              FROM system.size_estimates
             WHERE keyspace_name=? AND table_name=?
        """.trimIndent()
        return cql.queryForList(sql, cassProps.keyspace, table)
            .fold(0L) { acc, row ->
                val mean = (row["mean_partition_size"] as Number).toLong()
                val count = (row["partitions_count"] as Number).toLong()
                acc + mean * count
            }
    }
}
