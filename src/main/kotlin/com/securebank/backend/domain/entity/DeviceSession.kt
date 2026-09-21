package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "device_sessions")
class DeviceSession(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var deviceFingerprint: String = "",

    var deviceName: String? = null,
    var deviceOs: String? = null,
    var deviceOsVersion: String? = null,
    var appVersion: String? = null,

    @Column(nullable = false, length = 512)
    var refreshTokenHash: String = "",

    var ipAddress: String? = null,

    @Column(nullable = false)
    var isTrusted: Boolean = false,

    @Column(nullable = false)
    var lastActiveAt: Instant = Instant.now(),

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    var revokedAt: Instant? = null
)
