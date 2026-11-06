package org.ikigaidigital.adapter.persistence

import org.ikigaidigital.TimeDeposit
import org.ikigaidigital.application.port.outbound.TimeDepositRepository
import org.ikigaidigital.domain.toMoney
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Joins the transaction opened by [ExposedTransactionRunner]; never called outside one. */
class ExposedTimeDepositRepository : TimeDepositRepository {

    override fun findAll(): List<TimeDeposit> =
        TimeDepositsTable.selectAll()
            .orderBy(TimeDepositsTable.id)
            .map { it.toTimeDeposit() }

    // Locking in id order keeps the lock order the same for every caller, so two updates cannot deadlock.
    override fun findAllForUpdate(): List<TimeDeposit> =
        TimeDepositsTable.selectAll()
            .orderBy(TimeDepositsTable.id)
            .forUpdate()
            .map { it.toTimeDeposit() }

    override fun updateBalances(timeDeposits: List<TimeDeposit>) {
        timeDeposits.forEach { deposit ->
            TimeDepositsTable.update(where = { TimeDepositsTable.id eq deposit.id }) {
                it[TimeDepositsTable.balance] = deposit.balance.toMoney()
            }
        }
    }
}

private fun ResultRow.toTimeDeposit() = TimeDeposit(
    id = this[TimeDepositsTable.id],
    planType = this[TimeDepositsTable.planType],
    balance = this[TimeDepositsTable.balance].toDouble(),
    days = this[TimeDepositsTable.days],
)