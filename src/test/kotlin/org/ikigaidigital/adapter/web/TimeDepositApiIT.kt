package org.ikigaidigital.adapter.web

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.adapter.persistence.DatabaseConfig
import org.ikigaidigital.config.timeDepositModule
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.sql.DriverManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** HTTP -> routes -> use cases -> Exposed -> PostgreSQL in Docker. Every response is checked against the contract. */
class TimeDepositApiIT {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }
        private val databaseConfig = DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password)
        private val contract =
            OpenApiInteractionValidator.createForSpecificationUrl("openapi/time-deposits.yaml").build()
    }

    @Test
    fun `GET returns all time deposits with their withdrawals`() = apiTest {
        val response = client.get("/time-deposits")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertMatchesContract("/time-deposits", Request.Method.GET, response)
        assertThat(Json.parseToJsonElement(response.bodyAsText())).isEqualTo(
            Json.parseToJsonElement(
                """
                [
                  {"id": 1, "planType": "basic", "balance": 1000.00, "days": 31, "withdrawals": [
                    {"id": 1, "amount": 100.00, "date": "2026-10-01"},
                    {"id": 2, "amount": 50.50, "date": "2026-10-15"}
                  ]},
                  {"id": 2, "planType": "premium", "balance": 1000.00, "days": 46, "withdrawals": []},
                  {"id": 3, "planType": "student", "balance": 1000.00, "days": 366, "withdrawals": []}
                ]
                """,
            ),
        )
    }

    @Test
    fun `POST adds one month of interest to every time deposit`() = apiTest {
        val response = client.post("/time-deposits/balance-updates")

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertMatchesContract("/time-deposits/balance-updates", Request.Method.POST, response)
        assertThat(balances()).containsExactlyEntriesOf(
            mapOf(
                1 to BigDecimal("1000.83"), // basic
                2 to BigDecimal("1004.17"), // premium after 45 days
                3 to BigDecimal("1000.00"), // student older than one year
            ),
        )
    }

    @Test
    fun `POST is not idempotent - every call adds another month`() = apiTest {
        repeat(2) { client.post("/time-deposits/balance-updates") }

        assertThat(balances()[1]).isEqualByComparingTo("1001.66")
    }

    @Test
    fun `concurrent POSTs each add a month - no update is lost`() = apiTest {
        coroutineScope {
            repeat(2) { launch { client.post("/time-deposits/balance-updates") } }
        }

        assertThat(balances()[1]).isEqualByComparingTo("1001.66")
    }

    private fun apiTest(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { timeDepositModule(databaseConfig) }
        startApplication() // runs the Flyway migrations before the test data goes in
        sql("TRUNCATE TABLE withdrawals, time_deposits")
        sql(
            """
            INSERT INTO time_deposits (id, plan_type, days, balance)
            VALUES (1, 'basic', 31, 1000.00), (2, 'premium', 46, 1000.00), (3, 'student', 366, 1000.00)
            """,
        )
        sql(
            """
            INSERT INTO withdrawals (id, time_deposit_id, amount, date)
            VALUES (1, 1, 100.00, '2026-10-01'), (2, 1, 50.50, '2026-10-15')
            """,
        )
        test()
    }

    private suspend fun assertMatchesContract(path: String, method: Request.Method, response: HttpResponse) {
        val body = response.bodyAsText()
        val contractResponse = SimpleResponse.Builder.status(response.status.value).apply {
            response.contentType()?.let { withContentType(it.toString()) }
            if (body.isNotEmpty()) withBody(body)
        }.build()
        val report = contract.validateResponse(path, method, contractResponse)
        assertThat(report.hasErrors()).withFailMessage { report.messages.joinToString("\n") }.isFalse()
    }

    private fun sql(statement: String) =
        connection().use { connection -> connection.createStatement().use { it.execute(statement) } }

    private fun balances(): Map<Int, BigDecimal> = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, balance FROM time_deposits ORDER BY id").use { rows ->
                buildMap { while (rows.next()) put(rows.getInt("id"), rows.getBigDecimal("balance")) }
            }
        }
    }

    private fun connection() = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
}