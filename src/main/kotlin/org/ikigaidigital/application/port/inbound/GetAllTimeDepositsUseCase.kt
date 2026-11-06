package org.ikigaidigital.application.port.inbound

import org.ikigaidigital.TimeDeposit
import org.ikigaidigital.domain.Withdrawal

interface GetAllTimeDepositsUseCase {
    fun getAllTimeDeposits(): List<TimeDepositWithWithdrawals>
}

/** Read model: the shared TimeDeposit has no withdrawals field and must not change, so they travel next to it. */
data class TimeDepositWithWithdrawals(
    val timeDeposit: TimeDeposit,
    val withdrawals: List<Withdrawal>,
)