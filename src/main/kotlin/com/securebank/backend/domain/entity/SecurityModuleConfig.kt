package com.securebank.backend.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "security_module_configs")
class SecurityModuleConfig(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    var moduleId: String = "",

    @Column(nullable = false)
    var moduleName: String = "",

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(length = 2000)
    var config: String? = null,

    var lastModifiedBy: UUID? = null,

    @Column(nullable = false)
    var lastModifiedAt: Instant = Instant.now()
)
