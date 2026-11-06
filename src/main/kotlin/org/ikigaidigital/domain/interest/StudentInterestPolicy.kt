package org.ikigaidigital.domain.interest

import org.ikigaidigital.TimeDeposit

internal class StudentInterestPolicy : InterestPolicy {
    override val planType = "student"

    override fun monthlyInterest(deposit: TimeDeposit): Double =
        if (deposit.days <= LAST_DAY_WITH_INTEREST) monthlyShare(deposit.balance, ANNUAL_RATE) else 0.0

    private companion object {
        const val ANNUAL_RATE = 0.03

        // "No interest after 1 year": the original code pays interest while days < 366.
        const val LAST_DAY_WITH_INTEREST = 365
    }
}