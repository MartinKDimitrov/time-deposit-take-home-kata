package org.ikigaidigital

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.ikigaidigital.adapter.persistence.DatabaseConfig
import org.ikigaidigital.config.timeDepositModule
import org.testcontainers.postgresql.PostgreSQLContainer

/** `make run`: PostgreSQL in Docker (Testcontainers) with demo data, API on http://localhost:8080/swagger. */
fun main() {
    val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }
    val databaseConfig = DatabaseConfig(
        url = postgres.jdbcUrl,
        user = postgres.username,
        password = postgres.password,
        migrationLocations = listOf("classpath:db/migration", "classpath:db/seed"),
    )
    embeddedServer(Netty, port = 8080) { timeDepositModule(databaseConfig) }.start(wait = true)
}