package org.ikigaidigital.application.port.outbound

import org.ikigaidigital.domain.Withdrawal

interface WithdrawalRepository {
    /** All withdrawals, ordered by id. */
    fun findAll(): List<Withdrawal>
}