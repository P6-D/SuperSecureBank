package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class CardType { DEBIT, CREDIT }
enum class CardStatus { ACTIVE, FROZEN, CANCELLED, EXPIRED }

@Entity
@Table(name = "cards")
class Card(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var accountId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    var cardNumber: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var cardType: CardType = CardType.DEBIT,

    @Column(nullable = false)
    var expiryDate: LocalDate = LocalDate.now().plusYears(3),

    @Column(nullable = false)
    var cvv: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CardStatus = CardStatus.ACTIVE,

    @Column(nullable = false)
    var isVirtual: Boolean = false,

    @Column(nullable = false, precision = 18, scale = 4)
    var dailyLimit: BigDecimal = BigDecimal("2000.0000"),

    @Column(nullable = false, precision = 18, scale = 4)
    var monthlyLimit: BigDecimal = BigDecimal("20000.0000"),

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
