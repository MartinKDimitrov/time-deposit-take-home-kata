package org.ikigaidigital.application.service

import org.ikigaidigital.application.port.inbound.GetAllTimeDepositsUseCase
import org.ikigaidigital.application.port.inbound.TimeDepositWithWithdrawals
import org.ikigaidigital.application.port.outbound.TimeDepositRepository
import org.ikigaidigital.application.port.outbound.TransactionRunner
import org.ikigaidigital.application.port.outbound.WithdrawalRepository

class GetAllTimeDepositsService(
    private val timeDepositRepository: TimeDepositRepository,
    private val withdrawalRepository: WithdrawalRepository,
    private val transactionRunner: TransactionRunner,
) : GetAllTimeDepositsUseCase {

    override fun getAllTimeDeposits(): List<TimeDepositWithWithdrawals> = transactionRunner.inTransaction {
        // Two queries in total instead of one per deposit (no N+1).
        val withdrawalsByDepositId = withdrawalRepository.findAll().groupBy { it.timeDepositId }
        timeDepositRepository.findAll().map { deposit ->
            TimeDepositWithWithdrawals(deposit, withdrawalsByDepositId[deposit.id].orEmpty())
        }
    }
}