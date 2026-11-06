package org.ikigaidigital.adapter.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    /** Flyway locations; local runs add `classpath:db/seed` for demo data. */
    val migrationLocations: List<String> = listOf("classpath:db/migration"),
)

fun createDataSource(config: DatabaseConfig): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
        },
    )

/** Flyway owns the schema; Exposed only maps it. */
fun migrate(dataSource: DataSource, locations: List<String>) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(*locations.toTypedArray())
        .load()
        .migrate()
}