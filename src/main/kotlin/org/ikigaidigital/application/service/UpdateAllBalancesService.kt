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

    // Only balances change: days are not advanced here (A4) and withdrawals do not affect interest (A5), see
    // docs/PRODUCT.md#assumptions. The read and the write must stay in one transaction: findAllForUpdate holds the
    // row locks until it ends, so concurrent updates cannot lose a month.
    override fun updateAllBalances() = transactionRunner.inTransaction {
        val timeDeposits = timeDepositRepository.findAllForUpdate()
        calculator.updateBalance(timeDeposits)
        timeDepositRepository.updateBalances(timeDeposits)
    }
}