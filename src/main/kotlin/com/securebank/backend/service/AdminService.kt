package com.securebank.backend.service

import com.securebank.backend.api.admin.*
import com.securebank.backend.api.auth.UserResponseData
import com.securebank.backend.common.*
import com.securebank.backend.domain.entity.FraudCaseStatus
import com.securebank.backend.domain.entity.UserStatus
import com.securebank.backend.repository.AuditLogRepository
import com.securebank.backend.repository.FraudCaseRepository
import com.securebank.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** FR-BE-021 (User Management), FR-BE-022 (Audit Log Access), FR-BE-023 (Fraud Case Management). */
@Service
class AdminService(
    private val userRepository: UserRepository,
    private val auditLogRepository: AuditLogRepository,
    private val fraudCaseRepository: FraudCaseRepository,
    private val auditService: AuditService
) {
    fun listUsers(page: Int, pageSize: Int): PaginatedData<UserResponseData> {
        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = userRepository.findAll(pageable)
        return PaginatedData(
            items = result.content.map { UserResponseData.from(it) },
            page = page, pageSize = pageSize,
            totalCount = result.totalElements, totalPages = result.totalPages
        )
    }

    fun getUser(userId: UUID): UserResponseData {
        val user = userRepository.findById(userId).orElseThrow { notFound("User not found") }
        return UserResponseData.from(user)
    }

    @Transactional
    fun updateUserStatus(adminId: UUID, userId: UUID, request: UpdateUserStatusRequest): UserResponseData {
        val user = userRepository.findById(userId).orElseThrow { notFound("User not found") }
        val status = try {
            UserStatus.valueOf(request.status.uppercase())
        } catch (e: IllegalArgumentException) {
            badRequest("Invalid status: ${request.status}")
        }
        user.status = status
        user.updatedAt = Instant.now()
        userRepository.save(user)
        auditService.record(
            eventType = "ADMIN_USER_STATUS_UPDATED", adminId = adminId, userId = userId,
            eventData = mapOf("new_status" to status.name)
        )
        return UserResponseData.from(user)
    }

    /** FR-BE-021: "Soft delete user". */
    @Transactional
    fun softDeleteUser(adminId: UUID, userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { notFound("User not found") }
        user.deletedAt = Instant.now()
        user.status = UserStatus.CLOSED
        userRepository.save(user)
        auditService.record(eventType = "ADMIN_USER_DELETED", adminId = adminId, userId = userId)
    }

    /** FR-BE-022: audit logs are immutable — read-only access. */
    fun getAuditLogs(
        userId: UUID?, eventType: String?, startDate: String?, endDate: String?, page: Int, pageSize: Int
    ): PaginatedData<AuditLogResponseData> {
        val start = startDate?.let { Instant.parse(it) }
        val end = endDate?.let { Instant.parse(it) }
        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = auditLogRepository.search(userId, eventType, start, end, pageable)
        return PaginatedData(
            items = result.content.map { AuditLogResponseData.from(it) },
            page = page, pageSize = pageSize,
            totalCount = result.totalElements, totalPages = result.totalPages
        )
    }

    fun listFraudCases(page: Int, pageSize: Int): PaginatedData<FraudCaseResponseData> {
        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), pageSize)
        val result = fraudCaseRepository.findAllByOrderByCreatedAtDesc(pageable)
        return PaginatedData(
            items = result.content.map { FraudCaseResponseData.from(it) },
            page = page, pageSize = pageSize,
            totalCount = result.totalElements, totalPages = result.totalPages
        )
    }

    @Transactional
    fun updateFraudCase(adminId: UUID, caseId: UUID, request: UpdateFraudCaseRequest): FraudCaseResponseData {
        val fraudCase = fraudCaseRepository.findById(caseId).orElseThrow { notFound("Fraud case not found") }
        val status = try {
            FraudCaseStatus.valueOf(request.status.uppercase())
        } catch (e: IllegalArgumentException) {
            badRequest("Invalid status: ${request.status}")
        }
        fraudCase.status = status
        fraudCase.assignedToAdminId = adminId
        request.resolutionNote?.let { fraudCase.resolutionNote = it }
        fraudCaseRepository.save(fraudCase)
        auditService.record(eventType = "FRAUD_CASE_UPDATED", adminId = adminId, eventData = mapOf("case_id" to caseId.toString(), "status" to status.name))
        return FraudCaseResponseData.from(fraudCase)
    }

    @Transactional
    fun resolveFraudCase(adminId: UUID, caseId: UUID, request: ResolveFraudCaseRequest): FraudCaseResponseData {
        val fraudCase = fraudCaseRepository.findById(caseId).orElseThrow { notFound("Fraud case not found") }
        fraudCase.status = FraudCaseStatus.RESOLVED
        fraudCase.assignedToAdminId = adminId
        fraudCase.resolutionNote = request.resolutionNote
        fraudCase.resolvedAt = Instant.now()
        fraudCaseRepository.save(fraudCase)
        auditService.record(eventType = "FRAUD_CASE_RESOLVED", adminId = adminId, eventData = mapOf("case_id" to caseId.toString()))
        return FraudCaseResponseData.from(fraudCase)
    }
}
