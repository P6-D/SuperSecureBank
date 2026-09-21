package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AuditResult { SUCCESS, FAILURE, BLOCKED }

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id
    val id: UUID = UUID.randomUUID(),

    var userId: UUID? = null,

    var adminId: UUID? = null,

    @Column(nullable = false)
    var eventType: String = "",

    @Column(length = 4000)
    var eventData: String? = null,

    var ipAddress: String? = null,

    var deviceFingerprint: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var result: AuditResult = AuditResult.SUCCESS,

    var securityModuleTriggered: String? = null,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
)
