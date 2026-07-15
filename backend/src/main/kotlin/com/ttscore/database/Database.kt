package com.ttscore.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    val config = environment.config.config("database")
    val url = config.property("url").getString()
    val user = config.property("user").getString()
    val password = config.property("password").getString()

    // A pooled DataSource is essential: without it Exposed opens (and tears down) a fresh physical
    // connection per transaction, so every `dbQuery` pays a full TCP + auth handshake. That overhead
    // dominated transaction-chatty paths like the player sync. maxPoolSize is overridable so it can
    // be tuned to the prod DB's connection limit; 10 is safe for a single backend instance.
    val maxPoolSize = config.propertyOrNull("maxPoolSize")?.getString()?.toIntOrNull() ?: 10
    val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                driverClassName = config.property("driver").getString()
                username = user
                this.password = password
                maximumPoolSize = maxPoolSize
                minimumIdle = 2
                // Fail fast on an unreachable DB instead of hanging requests indefinitely.
                connectionTimeout = 10_000
                poolName = "ttscore-pool"
            },
        )

    Flyway.configure()
        .dataSource(dataSource)
        // Fail fast with the exact bad filename instead of silently skipping migrations
        // (seen with fat-jar classpath scanning misparsing otherwise-valid V*__*.sql names).
        .validateMigrationNaming(true)
        .load()
        .migrate()

    Database.connect(dataSource)
}

/**
 * Runs a blocking Exposed transaction on the IO dispatcher.
 * Use this instead of `withContext(Dispatchers.IO) { transaction { ... } }` everywhere.
 */
suspend fun <T> dbQuery(block: Transaction.() -> T): T = withContext(Dispatchers.IO) { transaction { block() } }
