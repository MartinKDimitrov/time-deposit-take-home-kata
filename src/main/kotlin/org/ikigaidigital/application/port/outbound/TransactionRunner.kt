package org.ikigaidigital.application.port.outbound

/** Runs a unit of work atomically, so use cases control transactions without knowing the persistence technology. */
interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}