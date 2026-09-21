package com.securebank.backend.api.account

import com.securebank.backend.common.AmountFormat
import com.securebank.backend.domain.entity.Account
import java.time.format.DateTimeFormatter

data class AccountResponseData(
    val id: String,
    val userId: String,
    val accountNumber: String,
    val accountType: String,
    val currency: String,
    val balance: String,
    val availableBalance: String,
    val status: String,
    val dailyTransferLimit: String,
    val createdAt: String
) {
    companion object {
        fun from(account: Account) = AccountResponseData(
            id = account.id.toString(),
            userId = account.userId.toString(),
            accountNumber = account.accountNumber,
            accountType = account.accountType.name,
            currency = account.currency,
            balance = AmountFormat.toWire(account.balance),
            availableBalance = AmountFormat.toWire(account.availableBalance),
            status = account.status.name,
            dailyTransferLimit = AmountFormat.toWire(account.dailyTransferLimit),
            createdAt = DateTimeFormatter.ISO_INSTANT.format(account.createdAt)
        )
    }
}

data class CreateAccountRequest(
    val userId: String,
    val accountType: String,
    val currency: String = "USD",
    val initialBalance: String = "0.00"
)
