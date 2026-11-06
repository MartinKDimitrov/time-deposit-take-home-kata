package org.ikigaidigital.application.service

import org.ikigaidigital.TimeDepositCalculator
import org.ikigaidigital.application.port.inbound.UpdateAllBalancesUseCase
import org.ikigaidigital.application.port.outbound.TimeDepositRepository
import org.ikigaidigital.application.port.outbound.TransactionRunner

class UpdateAllBalancesService(
    private val timeDepositRepository: TimeDepositRepository,
    private val calculator: TimeDepositCalculator,
    private val transactionRunner: TransactionRunner,
) : UpdateAllBalancesUseCase {

    override fun updateAllBalances() = transactionRunner.inTransaction {
        val timeDeposits = timeDepositRepository.findAll()
        calculator.updateBalance(timeDeposits)
        timeDepositRepository.updateBalances(timeDeposits)
    }
}