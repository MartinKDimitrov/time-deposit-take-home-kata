package org.ikigaidigital.adapter.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date

// Flyway owns the schema (db/migration); these objects only map it for queries.

internal object TimeDepositsTable : Table("time_deposits") {
    val id = integer("id")
    val planType = varchar("plan_type", 50)
    val days = integer("days")
    val balance = decimal("balance", 19, 2)

    override val primaryKey = PrimaryKey(id)
}

internal object WithdrawalsTable : Table("withdrawals") {
    val id = integer("id")
    val timeDepositId = integer("time_deposit_id").references(TimeDepositsTable.id)
    val amount = decimal("amount", 19, 2)
    val date = date("date")

    override val primaryKey = PrimaryKey(id)
}