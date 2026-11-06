package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
        "basic,   6.00,       31,  6.01", // 0.005 is rounded HALF_UP
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
}