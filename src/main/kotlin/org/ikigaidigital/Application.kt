package org.ikigaidigital

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.ikigaidigital.config.timeDepositModule

fun main() {
    embeddedServer(Netty, port = 8080) { timeDepositModule() }.start(wait = true)
}