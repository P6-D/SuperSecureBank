package com.securebank.backend.api.card

import com.securebank.backend.common.AmountFormat
import com.securebank.backend.domain.entity.Card
import java.time.format.DateTimeFormatter

data class IssueCardRequest(
    val accountId: String,
    val cardType: String
)

data class CardLimitsRequest(
    val dailyLimit: String,
    val monthlyLimit: String
)

data class CardResponseData(
    val id: String,
    val accountId: String,
    val cardNumber: String,
    val cardType: String,
    val expiryDate: String,
    val cvv: String,
    val status: String,
    val isVirtual: Boolean,
    val dailyLimit: String,
    val monthlyLimit: String,
    val createdAt: String
) {
    companion object {
        fun from(card: Card) = CardResponseData(
            id = card.id.toString(),
            accountId = card.accountId.toString(),
            cardNumber = card.cardNumber,
            cardType = card.cardType.name,
            expiryDate = card.expiryDate.toString(),
            cvv = card.cvv,
            status = card.status.name,
            isVirtual = card.isVirtual,
            dailyLimit = AmountFormat.toWire(card.dailyLimit),
            monthlyLimit = AmountFormat.toWire(card.monthlyLimit),
            createdAt = DateTimeFormatter.ISO_INSTANT.format(card.createdAt)
        )
    }
}
