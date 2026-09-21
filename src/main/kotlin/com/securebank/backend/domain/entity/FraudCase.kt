package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class FraudType { UNAUTHORIZED, WRONG_AMOUNT, DUPLICATE, PHISHING, OTHER }
enum class FraudCaseStatus { OPEN, UNDER_REVIEW, RESOLVED, DISMISSED }

@Entity
@Table(name = "fraud_cases")
class FraudCase(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var transactionId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var reportedByUserId: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var fraudType: FraudType = FraudType.OTHER,

    @Column(length = 2000)
    var description: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FraudCaseStatus = FraudCaseStatus.OPEN,

    var assignedToAdminId: UUID? = null,

    @Column(length = 2000)
    var resolutionNote: String? = null,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    var resolvedAt: Instant? = null
)
