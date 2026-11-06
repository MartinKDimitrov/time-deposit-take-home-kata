package org.ikigaidigital.domain.interest

import org.ikigaidigital.TimeDeposit

/** Monthly interest rule for one plan type. */
interface InterestPolicy {
    val planType: String

    fun monthlyInterest(deposit: TimeDeposit): Double
}

private const val MONTHS_IN_YEAR = 12

/** Keeps the arithmetic order of the original code (balance * rate / 12) so results stay identical. */
internal fun monthlyShare(balance: Double, annualRate: Double): Double =
    balance * annualRate / MONTHS_IN_YEAR