package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class TransactionType { TRANSFER_INTERNAL, TRANSFER_EXTERNAL, QR_PAYMENT, FEE, REVERSAL }
enum class TransactionStatus { PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, REVERSED }
enum class InitiatedBy { USER, SYSTEM, ADMIN }

@Entity
@Table(name = "transactions")
class Transaction(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    var idempotencyKey: String = "",

    @Column(nullable = false)
    var sourceAccountId: UUID = UUID.randomUUID(),

    var destinationAccountId: UUID? = null,

    var destinationAccountNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: TransactionType = TransactionType.TRANSFER_INTERNAL,

    @Column(nullable = false, precision = 18, scale = 4)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var currency: String = "USD",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TransactionStatus = TransactionStatus.PENDING,

    @Column(nullable = false)
    var reference: String = "",

    var description: String? = null,

    var anomalyScore: Float? = null,

    @Column(length = 2000)
    var anomalyFlags: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var initiatedBy: InitiatedBy = InitiatedBy.USER,

    var ipAddress: String? = null,

    var deviceFingerprint: String? = null,

    /** Owner of the source account — used for ownership/authorization checks. */
    @Column(nullable = false)
    var userId: UUID = UUID.randomUUID(),

    /** Set true once server-side step-up MFA has been satisfied for this transaction (FR-BE-012). */
    @Column(nullable = false)
    var stepUpVerified: Boolean = false,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    var settledAt: Instant? = null,

    var cancelledAt: Instant? = null
)
