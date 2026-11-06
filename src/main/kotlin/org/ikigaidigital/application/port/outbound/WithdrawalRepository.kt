package org.ikigaidigital.application.port.outbound

import org.ikigaidigital.domain.Withdrawal

/** Call inside [TransactionRunner.inTransaction]; implementations join that transaction. */
interface WithdrawalRepository {
    /** All withdrawals, ordered by id. */
    fun findAll(): List<Withdrawal>
}