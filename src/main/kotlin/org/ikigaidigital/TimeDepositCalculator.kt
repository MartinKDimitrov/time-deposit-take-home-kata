package org.ikigaidigital

import org.ikigaidigital.domain.interest.BasicInterestPolicy
import org.ikigaidigital.domain.interest.InterestPolicy
import org.ikigaidigital.domain.interest.PremiumInterestPolicy
import org.ikigaidigital.domain.interest.StudentInterestPolicy
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Adds one month of interest to the balance of every given deposit.
 *
 * The public API (no-arg constructor and `updateBalance(List<TimeDeposit>)`) is unchanged.
 * Plan specific rules live in [InterestPolicy] implementations, so a new plan is a new class.
 */
class TimeDepositCalculator(
    policies: List<InterestPolicy> = defaultPolicies(),
) {
    private val policiesByPlanType = policies.associateBy { it.planType }

    fun updateBalance(xs: List<TimeDeposit>) {
        xs.forEach { deposit ->
            deposit.balance += roundToCents(monthlyInterest(deposit))
        }
    }

    private fun monthlyInterest(deposit: TimeDeposit): Double {
        if (deposit.days <= INTEREST_FREE_DAYS) return 0.0
        // Unknown plan types earn no interest - same behaviour as the original implementation.
        return policiesByPlanType[deposit.planType]?.monthlyInterest(deposit) ?: 0.0
    }

    // BigDecimal(Double) is intentional: it keeps the exact rounding of the original implementation.
    private fun roundToCents(amount: Double): Double =
        BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toDouble()

    companion object {
        /** No plan earns interest during the first 30 days. */
        const val INTEREST_FREE_DAYS = 30

        fun defaultPolicies(): List<InterestPolicy> =
            listOf(BasicInterestPolicy(), StudentInterestPolicy(), PremiumInterestPolicy())
    }
}