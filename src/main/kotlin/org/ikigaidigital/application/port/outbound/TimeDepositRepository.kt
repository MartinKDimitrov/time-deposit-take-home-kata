package org.ikigaidigital.application.port.outbound

import org.ikigaidigital.TimeDeposit

/** Call inside [TransactionRunner.inTransaction]; implementations join that transaction. */
interface TimeDepositRepository {
    /** All time deposits, ordered by id. */
    fun findAll(): List<TimeDeposit>

    /** Persists only the balances of the given deposits. */
    fun updateBalances(timeDeposits: List<TimeDeposit>)

    /** All time deposits, ordered by id, locked until the transaction ends so concurrent balance updates run one after another. */
    fun findAllForUpdate(): List<TimeDeposit>
}