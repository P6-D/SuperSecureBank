package com.securebank.backend.repository

import com.securebank.backend.domain.entity.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailIgnoreCase(email: String): User?
    fun findByPhone(phone: String): User?
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun existsByPhone(phone: String): Boolean
}

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByUserId(userId: UUID): List<Account>
    fun findByAccountNumber(accountNumber: String): Account?
    fun existsByAccountNumber(accountNumber: String): Boolean
}

interface TransactionRepository : JpaRepository<Transaction, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): Transaction?

    @Query(
        """
        select t from Transaction t where t.userId = :userId
        and (:accountId is null or t.sourceAccountId = :accountId or t.destinationAccountId = :accountId)
        and (:type is null or t.type = :type)
        and (:status is null or t.status = :status)
        and (:startDate is null or t.createdAt >= :startDate)
        and (:endDate is null or t.createdAt <= :endDate)
        order by t.createdAt desc
        """
    )
    fun search(
        @Param("userId") userId: UUID,
        @Param("accountId") accountId: UUID?,
        @Param("type") type: TransactionType?,
        @Param("status") status: TransactionStatus?,
        @Param("startDate") startDate: Instant?,
        @Param("endDate") endDate: Instant?,
        pageable: Pageable
    ): Page<Transaction>

    @Query(
        """
        select t from Transaction t
        where (t.sourceAccountId = :accountId or t.destinationAccountId = :accountId)
        and t.createdAt >= :startDate and t.createdAt <= :endDate
        order by t.createdAt desc
        """
    )
    fun findForAccountStatement(
        @Param("accountId") accountId: UUID,
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant,
        pageable: Pageable
    ): Page<Transaction>

    @Query(
        "select coalesce(sum(t.amount), 0) from Transaction t where t.sourceAccountId = :accountId " +
            "and t.status in ('COMPLETED', 'PENDING', 'PROCESSING') and t.createdAt >= :since"
    )
    fun sumDebitsSince(@Param("accountId") accountId: UUID, @Param("since") since: Instant): java.math.BigDecimal
}

interface CardRepository : JpaRepository<Card, UUID> {
    fun findByUserId(userId: UUID): List<Card>
    fun findByAccountId(accountId: UUID): List<Card>
    fun existsByCardNumber(cardNumber: String): Boolean
}

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<Notification>
    fun findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<Notification>
}

interface DeviceSessionRepository : JpaRepository<DeviceSession, UUID> {
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<DeviceSession>
    fun findByRefreshTokenHash(hash: String): DeviceSession?
}

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    @Query(
        """
        select a from AuditLog a where
        (:userId is null or a.userId = :userId) and
        (:eventType is null or a.eventType = :eventType) and
        (:startDate is null or a.createdAt >= :startDate) and
        (:endDate is null or a.createdAt <= :endDate)
        order by a.createdAt desc
        """
    )
    fun search(
        @Param("userId") userId: UUID?,
        @Param("eventType") eventType: String?,
        @Param("startDate") startDate: Instant?,
        @Param("endDate") endDate: Instant?,
        pageable: Pageable
    ): Page<AuditLog>
}

interface SecurityModuleConfigRepository : JpaRepository<SecurityModuleConfig, UUID> {
    fun findByModuleId(moduleId: String): SecurityModuleConfig?
}

interface FraudCaseRepository : JpaRepository<FraudCase, UUID> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<FraudCase>
}
