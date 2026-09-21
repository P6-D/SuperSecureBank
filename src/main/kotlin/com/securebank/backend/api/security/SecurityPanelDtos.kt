package com.securebank.backend.api.security

import com.securebank.backend.domain.entity.SecurityModuleConfig
import java.time.format.DateTimeFormatter

data class ModuleToggleRequest(
    val enabled: Boolean,
    val config: Map<String, Any?>? = null
)

data class SecurityModuleResponseData(
    val moduleId: String,
    val moduleName: String,
    val enabled: Boolean,
    val config: String?,
    val lastModifiedBy: String?,
    val lastModifiedAt: String
) {
    companion object {
        fun from(m: SecurityModuleConfig) = SecurityModuleResponseData(
            moduleId = m.moduleId,
            moduleName = m.moduleName,
            enabled = m.enabled,
            config = m.config,
            lastModifiedBy = m.lastModifiedBy?.toString(),
            lastModifiedAt = DateTimeFormatter.ISO_INSTANT.format(m.lastModifiedAt)
        )
    }
}

data class AttackSimulationRequest(
    val attackType: String,
    val target: String
)

data class AttackSimulationResultData(
    val simulationId: String,
    val attackType: String,
    val target: String,
    val status: String,
    val detected: Boolean,
    val responseTimeMs: Long,
    val triggeredModule: String?
)

data class SecurityEventData(
    val id: String,
    val eventType: String,
    val result: String,
    val securityModuleTriggered: String?,
    val userId: String?,
    val ipAddress: String?,
    val createdAt: String
)

data class ScanTriggerRequest(
    val targetUrl: String? = null
)

data class ScanTriggerResponseData(
    val scanId: String,
    val status: String,
    val message: String
)
