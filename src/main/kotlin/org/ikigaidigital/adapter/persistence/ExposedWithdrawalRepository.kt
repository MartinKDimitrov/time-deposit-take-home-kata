package org.ikigaidigital.adapter.persistence

import org.ikigaidigital.application.port.outbound.WithdrawalRepository
import org.ikigaidigital.domain.Withdrawal
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Joins the transaction opened by [ExposedTransactionRunner]; never called outside one. */
class ExposedWithdrawalRepository : WithdrawalRepository {

    override fun findAll(): List<Withdrawal> =
        WithdrawalsTable.selectAll()
            .orderBy(WithdrawalsTable.id)
            .map { row ->
                Withdrawal(
                    id = row[WithdrawalsTable.id],
                    timeDepositId = row[WithdrawalsTable.timeDepositId],
                    amount = row[WithdrawalsTable.amount],
                    date = row[WithdrawalsTable.date],
                )
            }
}