package org.ikigaidigital.adapter.web

import kotlinx.serialization.Serializable
import org.ikigaidigital.application.port.inbound.TimeDepositWithWithdrawals
import org.ikigaidigital.domain.Withdrawal
import org.ikigaidigital.domain.toMoney
import java.math.BigDecimal

/** Response bodies; names and types follow the TimeDeposit and Withdrawal schemas of the contract. */
@Serializable
data class TimeDepositResponse(
    val id: Int,
    val planType: String,
    @Serializable(with = BigDecimalAsNumberSerializer::class) val balance: BigDecimal,
    val days: Int,
    val withdrawals: List<WithdrawalResponse>,
)

@Serializable
data class WithdrawalResponse(
    val id: Int,
    @Serializable(with = BigDecimalAsNumberSerializer::class) val amount: BigDecimal,
    /** ISO-8601 date (yyyy-MM-dd): `format: date` in the contract. */
    val date: String,
)

internal fun TimeDepositWithWithdrawals.toResponse() = TimeDepositResponse(
    id = timeDeposit.id,
    planType = timeDeposit.planType,
    balance = timeDeposit.balance.toMoney(),
    days = timeDeposit.days,
    withdrawals = withdrawals.map { it.toResponse() },
)

private fun Withdrawal.toResponse() = WithdrawalResponse(id = id, amount = amount, date = date.toString())