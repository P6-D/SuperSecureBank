package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class AccountType { CHECKING, SAVINGS, INVESTMENT }
enum class AccountStatus { ACTIVE, FROZEN, CLOSED }

@Entity
@Table(name = "accounts")
class Account(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    var accountNumber: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var accountType: AccountType = AccountType.CHECKING,

    @Column(nullable = false)
    var currency: String = "USD",

    @Column(nullable = false, precision = 18, scale = 4)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 18, scale = 4)
    var availableBalance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AccountStatus = AccountStatus.ACTIVE,

    @Column(nullable = false, precision = 18, scale = 4)
    var dailyTransferLimit: BigDecimal = BigDecimal("10000.0000"),

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
