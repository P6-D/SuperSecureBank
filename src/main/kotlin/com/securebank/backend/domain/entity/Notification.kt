package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class NotificationType { TRANSACTION, SECURITY, ACCOUNT, PROMOTION, SYSTEM }

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false, length = 1000)
    var message: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: NotificationType = NotificationType.SYSTEM,

    @Column(nullable = false)
    var isRead: Boolean = false,

    var referenceId: String? = null,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
