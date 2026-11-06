package org.ikigaidigital

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.ikigaidigital.adapter.persistence.DatabaseConfig
import org.ikigaidigital.config.timeDepositModule

/** Production entry point; the database comes from DB_URL, DB_USER and DB_PASSWORD. */
fun main() {
    val databaseConfig = DatabaseConfig(
        url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/timedeposits",
        user = System.getenv("DB_USER") ?: "timedeposits",
        password = System.getenv("DB_PASSWORD") ?: "timedeposits",
    )
    embeddedServer(Netty, port = 8080) { timeDepositModule(databaseConfig) }.start(wait = true)
}