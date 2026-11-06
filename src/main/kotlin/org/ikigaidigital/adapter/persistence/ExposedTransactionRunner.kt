package org.ikigaidigital.adapter.persistence

import org.ikigaidigital.application.port.outbound.TransactionRunner
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

/** Repository calls inside the block join this transaction, so the whole block commits or rolls back together. */
class ExposedTransactionRunner(dataSource: DataSource) : TransactionRunner {
    private val database = Database.connect(dataSource)

    override fun <T> inTransaction(block: () -> T): T = transaction(database) { block() }
}