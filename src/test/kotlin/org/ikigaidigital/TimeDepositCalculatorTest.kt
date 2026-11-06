package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.ikigaidigital.domain.interest.InterestPolicy

class TimeDepositCalculatorTest {

    private val calculator = TimeDepositCalculator()

    @ParameterizedTest(name = "{0}, {2} days: {1} -> {3}")
    @CsvSource(
        // planType, balance, days, expected balance
        "basic,   1000.00,    30,  1000.00", // no interest during the first 30 days
        "basic,   1000.00,    31,  1000.83",
        "basic,   1234567.00, 45,  1235595.81",
        "student, 1000.00,    30,  1000.00",
        "student, 1000.00,    31,  1002.50",
        "student, 1000.00,    365, 1002.50", // last day with interest
        "student, 1000.00,    366, 1000.00", // no interest after one year
        "premium, 1000.00,    31,  1000.00",
        "premium, 1000.00,    45,  1000.00", // interest starts after 45 days
        "premium, 1000.00,    46,  1004.17",
        "unknown, 1000.00,    100, 1000.00", // unknown plan types earn nothing
        "basic,   6.00,       31,  6.01", // smallest balance that earns a cent
        "basic,   150.00,     31,  150.13", // exact tie 0.125: HALF_UP, not HALF_EVEN or HALF_DOWN
        "basic,   18.00,      31,  18.01", // 0.01499999...: BigDecimal(Double), not BigDecimal.valueOf
        "student, 10.00,      31,  10.02", // 0.02499999...: arithmetic order balance * rate / 12
        "premium, 3.60,       46,  3.62", // 0.01500000...1: arithmetic order balance * rate / 12
        "Basic,   1000.00,    31,  1000.00", // plan types are case-sensitive, as in the original
    )
    fun `adds one month of interest according to the plan`(
        planType: String,
        balance: Double,
        days: Int,
        expectedBalance: Double,
    ) {
        val deposit = TimeDeposit(1, planType, balance, days)

        calculator.updateBalance(listOf(deposit))

        assertThat(deposit.balance).isEqualTo(expectedBalance)
    }

    @Test
    fun `updates every deposit in the list and leaves the other fields unchanged`() {
        val deposits = listOf(
            TimeDeposit(1, "basic", 1000.0, 31),
            TimeDeposit(2, "premium", 1000.0, 46),
        )

        calculator.updateBalance(deposits)

        assertThat(deposits).containsExactly(
            TimeDeposit(1, "basic", 1000.83, 31),
            TimeDeposit(2, "premium", 1004.17, 46),
        )
    }

    private val vipPolicy = object : InterestPolicy {
        override val planType = "vip"
        override fun monthlyInterest(deposit: TimeDeposit) = 10.0
    }

    @Test
    fun `new plan types are supported by adding an interest policy`() {
        val calculator = TimeDepositCalculator(TimeDepositCalculator.defaultPolicies() + vipPolicy)
        val deposit = TimeDeposit(1, "vip", 1000.0, 31)

        calculator.updateBalance(listOf(deposit))

        assertThat(deposit.balance).isEqualTo(1010.0)
    }

    @Test
    fun `new plan types earn nothing during the first 30 days`() {
        val calculator = TimeDepositCalculator(TimeDepositCalculator.defaultPolicies() + vipPolicy)
        val deposit = TimeDeposit(1, "vip", 1000.0, 30)

        calculator.updateBalance(listOf(deposit))

        assertThat(deposit.balance).isEqualTo(1000.0)
    }
}