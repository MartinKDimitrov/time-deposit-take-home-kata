package org.ikigaidigital.domain.interest

import org.ikigaidigital.TimeDeposit

internal class BasicInterestPolicy : InterestPolicy {
    override val planType = "basic"

    override fun monthlyInterest(deposit: TimeDeposit): Double =
        monthlyShare(deposit.balance, ANNUAL_RATE)

    private companion object {
        const val ANNUAL_RATE = 0.01
    }
}