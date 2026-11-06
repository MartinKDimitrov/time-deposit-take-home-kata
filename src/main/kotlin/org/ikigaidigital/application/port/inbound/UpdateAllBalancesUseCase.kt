package org.ikigaidigital.application.port.inbound

interface UpdateAllBalancesUseCase {
    /** Adds one month of interest to every time deposit. Not idempotent. */
    fun updateAllBalances()
}