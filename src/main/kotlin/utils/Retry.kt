package main.utils

import kotlinx.coroutines.delay

object Retry {

    suspend inline fun <T> retry(
        attempts: Int = 10,
        initialDelayMs: Long = 100,
        factor: Double = 2.0,
        crossinline block: suspend () -> T
    ): T {
        var delayMs = initialDelayMs

        repeat(attempts - 1) { idx ->
            try {
                return block()
            } catch (ex: Throwable) {
                println("retry[${idx + 1}/$attempts] failed: ${ex.message}")
                delay(delayMs)
                delayMs = (delayMs * factor).toLong()
            }
        }

        return try {
            block()
        } catch (ex: Throwable) {
            println("retry[$attempts/$attempts] failed: ${ex.message}")
            throw ex
        }
    }
}
