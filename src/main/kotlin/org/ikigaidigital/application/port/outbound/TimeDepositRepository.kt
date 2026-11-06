package org.ikigaidigital.application.port.outbound

import org.ikigaidigital.TimeDeposit

interface TimeDepositRepository {
    /** All time deposits, ordered by id. */
    fun findAll(): List<TimeDeposit>

    /** Persists only the balances of the given deposits. */
    fun updateBalances(timeDeposits: List<TimeDeposit>)
}