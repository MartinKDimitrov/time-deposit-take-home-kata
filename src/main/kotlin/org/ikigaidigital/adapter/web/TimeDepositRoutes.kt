package org.ikigaidigital.adapter.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ikigaidigital.application.port.inbound.GetAllTimeDepositsUseCase
import org.ikigaidigital.application.port.inbound.UpdateAllBalancesUseCase

/** The two endpoints of openapi/time-deposits.yaml. The use cases block on JDBC, so they run on Dispatchers.IO. */
fun Route.timeDepositRoutes(
    getAllTimeDeposits: GetAllTimeDepositsUseCase,
    updateAllBalances: UpdateAllBalancesUseCase,
) {
    route("/time-deposits") {
        get {
            val timeDeposits = withContext(Dispatchers.IO) { getAllTimeDeposits.getAllTimeDeposits() }
            call.respond(timeDeposits.map { it.toResponse() })
        }
        post("/balance-updates") {
            withContext(Dispatchers.IO) { updateAllBalances.updateAllBalances() }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}