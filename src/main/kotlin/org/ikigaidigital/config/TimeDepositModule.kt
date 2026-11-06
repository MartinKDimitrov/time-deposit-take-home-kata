package org.ikigaidigital.config

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import org.ikigaidigital.TimeDepositCalculator
import org.ikigaidigital.adapter.persistence.DatabaseConfig
import org.ikigaidigital.adapter.persistence.ExposedTimeDepositRepository
import org.ikigaidigital.adapter.persistence.ExposedTransactionRunner
import org.ikigaidigital.adapter.persistence.ExposedWithdrawalRepository
import org.ikigaidigital.adapter.persistence.createDataSource
import org.ikigaidigital.adapter.persistence.migrate
import org.ikigaidigital.adapter.web.timeDepositRoutes
import org.ikigaidigital.application.service.GetAllTimeDepositsService
import org.ikigaidigital.application.service.UpdateAllBalancesService

/** Composition root: the only place that knows every adapter and wires them to the use cases. */
fun Application.timeDepositModule(databaseConfig: DatabaseConfig) {
    val dataSource = createDataSource(databaseConfig)
    monitor.subscribe(ApplicationStopped) { dataSource.close() }
    migrate(dataSource, databaseConfig.migrationLocations)

    val transactionRunner = ExposedTransactionRunner(dataSource)
    val timeDepositRepository = ExposedTimeDepositRepository()
    val getAllTimeDeposits =
        GetAllTimeDepositsService(timeDepositRepository, ExposedWithdrawalRepository(), transactionRunner)
    val updateAllBalances =
        UpdateAllBalancesService(timeDepositRepository, TimeDepositCalculator(), transactionRunner)

    install(ContentNegotiation) { json() }
    routing {
        timeDepositRoutes(getAllTimeDeposits, updateAllBalances)
        swaggerUI(path = "swagger", swaggerFile = "openapi/time-deposits.yaml")
    }
}