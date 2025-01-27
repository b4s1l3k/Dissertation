package main

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [CassandraAutoConfiguration::class, CassandraDataAutoConfiguration::class])
class MainApplication

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
}