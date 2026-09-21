package com.securebank.backend.service

import com.securebank.backend.api.card.CardLimitsRequest
import com.securebank.backend.api.card.CardResponseData
import com.securebank.backend.api.card.IssueCardRequest
import com.securebank.backend.common.*
import com.securebank.backend.domain.entity.Card
import com.securebank.backend.domain.entity.CardStatus
import com.securebank.backend.domain.entity.CardType
import com.securebank.backend.repository.CardRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Not explicitly enumerated as FR-BE-0xx in the FRD text but required by
 * README §5.5 `CardApiService.kt` — grouped under the Account/Transaction
 * services domain for the same ownership/security rules (ATK-012 IDOR
 * defense applies equally to cards).
 */
@Service
class CardService(
    private val cardRepository: CardRepository,
    private val accountService: AccountService,
    private val auditService: AuditService
) {
    fun list(userId: UUID, accountId: UUID?): List<CardResponseData> {
        val cards = if (accountId != null) {
            accountService.findOwnedAccount(userId, accountId)
            cardRepository.findByAccountId(accountId)
        } else {
            cardRepository.findByUserId(userId)
        }
        return cards.map { CardResponseData.from(it) }
    }

    @Transactional
    fun issueVirtualCard(userId: UUID, request: IssueCardRequest): CardResponseData {
        val account = accountService.findOwnedAccount(userId, UUID.fromString(request.accountId))
        val cardType = try {
            CardType.valueOf(request.cardType.uppercase())
        } catch (e: IllegalArgumentException) {
            badRequest("Invalid card_type: ${request.cardType}")
        }
        var cardNumber: String
        do {
            cardNumber = CardNumberGenerator.generate()
        } while (cardRepository.existsByCardNumber(cardNumber))

        val card = Card(
            accountId = account.id,
            userId = userId,
            cardNumber = cardNumber,
            cardType = cardType,
            cvv = CardNumberGenerator.generateCvv(),
            isVirtual = true
        )
        cardRepository.save(card)
        auditService.record(eventType = "CARD_ISSUED", userId = userId, eventData = mapOf("card_id" to card.id.toString()))
        return CardResponseData.from(card)
    }

    @Transactional
    fun freeze(userId: UUID, cardId: UUID): CardResponseData = setStatus(userId, cardId, CardStatus.FROZEN, "CARD_FROZEN")

    @Transactional
    fun unfreeze(userId: UUID, cardId: UUID): CardResponseData = setStatus(userId, cardId, CardStatus.ACTIVE, "CARD_UNFROZEN")

    private fun setStatus(userId: UUID, cardId: UUID, status: CardStatus, eventType: String): CardResponseData {
        val card = findOwnedCard(userId, cardId)
        if (card.status == CardStatus.CANCELLED || card.status == CardStatus.EXPIRED) {
            unprocessable("Cannot change status of a ${card.status.name.lowercase()} card")
        }
        card.status = status
        cardRepository.save(card)
        auditService.record(eventType = eventType, userId = userId, eventData = mapOf("card_id" to cardId.toString()))
        return CardResponseData.from(card)
    }

    @Transactional
    fun updateLimits(userId: UUID, cardId: UUID, request: CardLimitsRequest): CardResponseData {
        val card = findOwnedCard(userId, cardId)
        card.dailyLimit = AmountFormat.parse(request.dailyLimit)
        card.monthlyLimit = AmountFormat.parse(request.monthlyLimit)
        cardRepository.save(card)
        auditService.record(eventType = "CARD_LIMITS_UPDATED", userId = userId, eventData = mapOf("card_id" to cardId.toString()))
        return CardResponseData.from(card)
    }

    private fun findOwnedCard(userId: UUID, cardId: UUID): Card {
        val card = cardRepository.findById(cardId).orElseThrow { notFound("Card not found") }
        if (card.userId != userId) forbidden("You do not have access to this card")
        return card
    }
}
