package org.ikigaidigital.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The shared TimeDeposit keeps its balance as Double and must not change.
 * Outside the domain the balance is a 2-decimal BigDecimal; this is the single conversion point.
 */
fun Double.toMoney(): BigDecimal = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)