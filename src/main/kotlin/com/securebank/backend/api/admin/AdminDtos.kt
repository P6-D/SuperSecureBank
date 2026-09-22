package com.securebank.backend.api.admin

import com.securebank.backend.api.auth.UserResponseData
import com.securebank.backend.domain.entity.AuditLog
import com.securebank.backend.domain.entity.FraudCase
import jakarta.validation.constraints.NotBlank
data class AddBalanceRequest(
    @field:NotBlank val amount: String,
    val description: String? = null
)

import java.time.format.DateTimeFormatter

data class UpdateUserStatusRequest(
    @field:NotBlank val status: String
)

data class AuditLogResponseData(
    val id: String,
    val userId: String?,
    val adminId: String?,
    val eventType: String,
    val eventData: String?,
    val ipAddress: String?,
    val deviceFingerprint: String?,
    val result: String,
    val securityModuleTriggered: String?,
    val createdAt: String
) {
    companion object {
        fun from(log: AuditLog) = AuditLogResponseData(
            id = log.id.toString(),
            userId = log.userId?.toString(),
            adminId = log.adminId?.toString(),
            eventType = log.eventType,
            eventData = log.eventData,
            ipAddress = log.ipAddress,
            deviceFingerprint = log.deviceFingerprint,
            result = log.result.name,
            securityModuleTriggered = log.securityModuleTriggered,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(log.createdAt)
        )
    }
}

data class UpdateFraudCaseRequest(
    @field:NotBlank val status: String,
    val resolutionNote: String? = null
)

data class ResolveFraudCaseRequest(
    val resolutionNote: String? = null
)

data class FraudCaseResponseData(
    val id: String,
    val transactionId: String,
    val reportedByUserId: String,
    val fraudType: String,
    val description: String,
    val status: String,
    val assignedToAdminId: String?,
    val resolutionNote: String?,
    val createdAt: String,
    val resolvedAt: String?
) {
    companion object {
        fun from(fraudCase: FraudCase) = FraudCaseResponseData(
            id = fraudCase.id.toString(),
            transactionId = fraudCase.transactionId.toString(),
            reportedByUserId = fraudCase.reportedByUserId.toString(),
            fraudType = fraudCase.fraudType.name,
            description = fraudCase.description,
            status = fraudCase.status.name,
            assignedToAdminId = fraudCase.assignedToAdminId?.toString(),
            resolutionNote = fraudCase.resolutionNote,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(fraudCase.createdAt),
            resolvedAt = fraudCase.resolvedAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
        )
    }
}
