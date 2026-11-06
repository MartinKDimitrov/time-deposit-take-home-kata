package org.ikigaidigital.config

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/** Composition root: the only place that knows every adapter and wires them to the use cases. */
fun Application.timeDepositModule() {
    install(ContentNegotiation) { json() }
}