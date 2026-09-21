package com.securebank.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.securebank.backend.domain.entity.AuditLog
import com.securebank.backend.domain.entity.AuditResult
import com.securebank.backend.repository.AuditLogRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * FR-BE-022 / FRD §10.5: immutable audit trail. Also backs FR-BE-010 "Balance
 * access logged to audit trail" and general security-event logging referenced
 * throughout §7 (login, logout, MFA, transfers, card actions).
 */
@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    fun record(
        eventType: String,
        userId: UUID? = null,
        adminId: UUID? = null,
        eventData: Map<String, Any?> = emptyMap(),
        ipAddress: String? = null,
        deviceFingerprint: String? = null,
        result: AuditResult = AuditResult.SUCCESS,
        securityModuleTriggered: String? = null
    ) {
        val log = AuditLog(
            userId = userId,
            adminId = adminId,
            eventType = eventType,
            eventData = if (eventData.isEmpty()) null else objectMapper.writeValueAsString(eventData),
            ipAddress = ipAddress,
            deviceFingerprint = deviceFingerprint,
            result = result,
            securityModuleTriggered = securityModuleTriggered
        )
        auditLogRepository.save(log)
    }
}
