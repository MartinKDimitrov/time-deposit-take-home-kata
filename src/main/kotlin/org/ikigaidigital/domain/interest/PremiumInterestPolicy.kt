package org.ikigaidigital.domain.interest

import org.ikigaidigital.TimeDeposit

internal class PremiumInterestPolicy : InterestPolicy {
    override val planType = "premium"

    override fun monthlyInterest(deposit: TimeDeposit): Double =
        if (deposit.days > INTEREST_STARTS_AFTER_DAYS) monthlyShare(deposit.balance, ANNUAL_RATE) else 0.0

    private companion object {
        const val ANNUAL_RATE = 0.05
        const val INTEREST_STARTS_AFTER_DAYS = 45
    }
}