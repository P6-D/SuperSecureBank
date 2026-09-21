package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class UserStatus { PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, CLOSED }

/** Not part of FRD data model but required for RBAC on Admin API / Security Control Panel. */
enum class UserRole { USER, ADMIN }

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var fullName: String = "",

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(nullable = false, unique = true)
    var phone: String = "",

    @Column(nullable = false)
    var nationalId: String = "",

    @Column(nullable = false)
    var passwordHash: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.PENDING_VERIFICATION,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole = UserRole.USER,

    @Column(nullable = false)
    var mfaEnabled: Boolean = false,

    @Column(length = 512)
    var mfaSecretEncrypted: String? = null,

    @Column(nullable = false)
    var failedLoginAttempts: Int = 0,

    var lastLoginAt: Instant? = null,

    var lastLoginIp: String? = null,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null
)
