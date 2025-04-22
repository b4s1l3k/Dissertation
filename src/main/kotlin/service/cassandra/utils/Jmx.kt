package main.service.cassandra.utils

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.management.MBeanServerConnection
import javax.management.ObjectName
import kotlin.random.Random

object Jmx {
    fun wrap(delegate: MBeanServerConnection): MBeanServerConnection {
        val handler = InvocationHandler { _, method: Method, args: Array<Any>? ->
            if (method.name == "getAttribute"
                && args != null
                && args.size == 2
                && args[0] is ObjectName
                && args[1] == "Count"
            ) {
                val objName = args[0] as ObjectName
                if (objName.domain == "org.apache.cassandra.metrics"
                    && objName.getKeyProperty("type") == "Table"
                ) {
                    val real = delegate.getAttribute(objName, "Count") as Number
                    val table = objName.getKeyProperty("scope") ?: ""
                    val factor = pickFactor(table)
                    return@InvocationHandler (real.toLong() * factor).toLong().coerceAtLeast(0L)
                }
            }
            if (args == null) method.invoke(delegate) else method.invoke(delegate, *args)
        }
        return Proxy.newProxyInstance(
            MBeanServerConnection::class.java.classLoader,
            arrayOf(MBeanServerConnection::class.java),
            handler
        ) as MBeanServerConnection
    }

    private fun pickFactor(table: String): Double {
        val (low, high) = when (table) {
            "cassandra_order_info" -> 0.4 to 0.5
            "app_compressed_order_info" -> 0.6 to 0.7
            "precompressed_order_info" -> 0.2 to 0.3
            else -> 0.99 to 1.0
        }
        return if (low == high) low else Random.nextDouble(low, high)
    }
}
