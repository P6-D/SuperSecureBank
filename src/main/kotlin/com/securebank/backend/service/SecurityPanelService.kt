package com.securebank.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.securebank.backend.api.security.*
import com.securebank.backend.common.badRequest
import com.securebank.backend.common.notFound
import com.securebank.backend.domain.entity.SecurityModuleConfig
import com.securebank.backend.repository.AuditLogRepository
import com.securebank.backend.repository.SecurityModuleConfigRepository
import jakarta.annotation.PostConstruct
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/** FR-BE-024 to FR-BE-027: Security Control Panel (Testbed-Specific), FRD §7.6 / §8.1. */
@Service
class SecurityPanelService(
    private val moduleRepository: SecurityModuleConfigRepository,
    private val auditLogRepository: AuditLogRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {
    /** FRD §8.1 Module Registry — seeded at startup so the panel has data without a manual admin step. */
    @PostConstruct
    fun seedModules() {
        if (moduleRepository.count() > 0) return
        MODULE_REGISTRY.forEach { (id, name, defaultOn) ->
            moduleRepository.save(SecurityModuleConfig(moduleId = id, moduleName = name, enabled = defaultOn))
        }
    }

    fun listModules(): List<SecurityModuleResponseData> =
        moduleRepository.findAll().sortedBy { it.moduleId }.map { SecurityModuleResponseData.from(it) }

    /** FR-BE-024: Enable/disable security modules at runtime without restart. */
    @Transactional
    fun toggleModule(adminId: UUID, moduleName: String, request: ModuleToggleRequest): SecurityModuleResponseData {
        val module = moduleRepository.findByModuleId(moduleName) ?: notFound("Security module not found: $moduleName")
        module.enabled = request.enabled
        request.config?.let { module.config = objectMapper.writeValueAsString(it) }
        module.lastModifiedBy = adminId
        module.lastModifiedAt = Instant.now()
        moduleRepository.save(module)
        auditService.record(
            eventType = "SECURITY_MODULE_TOGGLED", adminId = adminId,
            eventData = mapOf("module_id" to module.moduleId, "enabled" to request.enabled),
            securityModuleTriggered = module.moduleId
        )
        return SecurityModuleResponseData.from(module)
    }

    /** FR-BE-025: Trigger a simulated attack scenario for testing purposes. */
    fun simulateAttack(adminId: UUID, request: AttackSimulationRequest): AttackSimulationResultData {
        if (!ATTACK_TYPES.contains(request.attackType.lowercase())) {
            badRequest("Unknown attack_type: ${request.attackType}")
        }
        val start = System.nanoTime()
        // Deterministic-ish stand-in "detection" result — this backend has no real WAF/IDS behind it,
        // so simulation only records the event for the blue-team dashboard rather than performing a real attack.
        val detected = Random.nextInt(100) < 85
        val responseTimeMs = (System.nanoTime() - start) / 1_000_000
        val simulationId = UUID.randomUUID().toString()

        auditService.record(
            eventType = "ATTACK_SIMULATION", adminId = adminId,
            eventData = mapOf(
                "simulation_id" to simulationId, "attack_type" to request.attackType,
                "target" to request.target, "detected" to detected
            ),
            result = if (detected) com.securebank.backend.domain.entity.AuditResult.BLOCKED else com.securebank.backend.domain.entity.AuditResult.SUCCESS,
            securityModuleTriggered = if (detected) "MOD-001" else null
        )

        return AttackSimulationResultData(
            simulationId = simulationId,
            attackType = request.attackType,
            target = request.target,
            status = "COMPLETED",
            detected = detected,
            responseTimeMs = responseTimeMs,
            triggeredModule = if (detected) "MOD-001" else null
        )
    }

    /** FR-BE-026: recent security events, polled snapshot (SSE/WebSocket streaming is a future enhancement). */
    fun recentEvents(limit: Int): List<SecurityEventData> {
        val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        return auditLogRepository.search(null, null, null, null, pageable).content.map {
            SecurityEventData(
                id = it.id.toString(), eventType = it.eventType, result = it.result.name,
                securityModuleTriggered = it.securityModuleTriggered,
                userId = it.userId?.toString(), ipAddress = it.ipAddress,
                createdAt = java.time.format.DateTimeFormatter.ISO_INSTANT.format(it.createdAt)
            )
        }
    }

    /** FR-BE-027: Trigger automated DAST scan (integrates with OWASP ZAP / Burp API). */
    fun triggerScan(adminId: UUID, request: ScanTriggerRequest): ScanTriggerResponseData {
        val scanId = UUID.randomUUID().toString()
        auditService.record(
            eventType = "VULN_SCAN_TRIGGERED", adminId = adminId,
            eventData = mapOf("scan_id" to scanId, "target_url" to request.targetUrl)
        )
        // No real OWASP ZAP/Burp integration wired up in this testbed backend; recorded for audit/demo purposes.
        return ScanTriggerResponseData(
            scanId = scanId, status = "QUEUED",
            message = "Scan queued (no external DAST engine configured in this testbed backend)"
        )
    }

    companion object {
        private val MODULE_REGISTRY = listOf(
            Triple("MOD-001", "WAF (OWASP Top 10)", true),
            Triple("MOD-002", "Rate Limiter", true),
            Triple("MOD-003", "Brute Force Protection", true),
            Triple("MOD-004", "JWT Signature Verify", true),
            Triple("MOD-005", "mTLS Enforcement", true),
            Triple("MOD-006", "Certificate Pinning", true),
            Triple("MOD-007", "Root Detection", true),
            Triple("MOD-008", "Emulator Detection", true),
            Triple("MOD-009", "Overlay Attack Guard", true),
            Triple("MOD-010", "Biometric Auth", true),
            Triple("MOD-011", "TOTP MFA", false),
            Triple("MOD-012", "Anomaly Detection", true),
            Triple("MOD-013", "Velocity Check", true),
            Triple("MOD-014", "RASP Agent", false),
            Triple("MOD-015", "Payload Encryption E2E", false),
            Triple("MOD-016", "Anti-Frida Hook Guard", false),
            Triple("MOD-017", "SQL Injection Filter", true),
            Triple("MOD-018", "Audit Trail", true),
            Triple("MOD-019", "Geo-Fencing", false),
            Triple("MOD-020", "Device Fingerprinting", true)
        )
        private val ATTACK_TYPES = setOf(
            "sql_injection", "brute_force", "token_replay", "mitm", "session_fixation",
            "xss", "race_condition", "frida_hooking", "root_device_login", "credential_stuffing",
            "qr_token_replay", "idor"
        )
    }
}
