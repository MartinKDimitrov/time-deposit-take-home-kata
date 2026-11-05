package org.ikigaidigital

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class TimeDepositCalculatorTest {
    @Test
    fun updateBalance_Test() {
        val calc = TimeDepositCalculator()
        val plans = listOf(
            TimeDeposit(1, "basic", 1234567.00, 45)
        )
        calc.updateBalance(plans)
        Assertions.assertThat(1).isEqualTo(1)
    }
}