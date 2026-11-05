package org.ikigaidigital

import java.math.BigDecimal
import java.math.RoundingMode

class TimeDepositCalculator {
    fun updateBalance(xs: List<TimeDeposit>) {
        for (i in xs.indices) {
            var interest = 0.0
            if (xs[i].days > 30) {
                if (xs[i].planType == "student") {
                    if (xs[i].days < 366) {
                        interest += xs[i].balance * 0.03 / 12
                    }
                } else if (xs[i].planType == "premium") {
                    if (xs[i].days > 45) {
                        interest += xs[i].balance * 0.05 / 12
                    }
                } else if (xs[i].planType == "basic") {
                    interest += xs[i].balance * 0.01 / 12
                }
            }
            val a2d = BigDecimal(interest).setScale(2, RoundingMode.HALF_UP)
            xs[i].balance += a2d.toDouble()
        }
    }
}