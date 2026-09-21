package com.securebank.backend.service

import com.securebank.backend.api.transaction.*
import com.securebank.backend.cache.IdempotencyLockStore
import com.securebank.backend.cache.QrTokenConsumptionStore
import com.securebank.backend.common.*
import com.securebank.backend.config.SecureBankProperties
import com.securebank.backend.domain.entity.*
import com.securebank.backend.repository.AccountRepository
import com.securebank.backend.repository.CardRepository
import com.securebank.backend.repository.FraudCaseRepository
import com.securebank.backend.repository.TransactionRepository
import com.securebank.backend.repository.UserRepository
import com.securebank.backend.security.AesEncryptor
import com.securebank.backend.security.JwtService
import com.securebank.backend.security.TokenType
import com.warrenstrange.googleauth.GoogleAuthenticator
import io.jsonwebtoken.JwtException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class TransactionService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val cardRepository: CardRepository,
    private val fraudCaseRepository: FraudCaseRepository,
    private val userRepository: UserRepository,
    private val accountService: AccountService,
    private val notificationService: NotificationService,
    private val auditService: AuditService,
    private val jwtService: JwtService,
    private val aesEncryptor: AesEncryptor,
    private val idempotencyLockStore: IdempotencyLockStore,
    private val qrTokenConsumptionStore: QrTokenConsumptionStore,
    private val props: SecureBankProperties
) {
    private val totp = GoogleAuthenticator()

    /** FR-BE-012: Initiate Transfer. `stepUpCode` = optional `X-Step-Up-Code` header (TOTP code). */
    @Transactional
    fun transfer(
        userId: UUID,
        request: TransferRequest,
        idempotencyKeyHeader: String?,
        stepUpCode: String?,
        ip: String?,
        deviceFingerprint: String?
    ): TransactionResponseData {
        val idempotencyKey = idempotencyKeyHeader ?: request.idempotencyKey
        if (idempotencyKey.isBlank()) badRequest("Idempotency-Key is required")

        // Fast path: return the existing result for a retried request (idempotency, ATK-007 defense).
        transactionRepository.findByIdempotencyKey(idempotencyKey)?.let {
            if (it.userId != userId) forbidden("Idempotency key belongs to another user")
            return TransactionResponseData.from(it)
        }

        if (!idempotencyLockStore.tryLock(idempotencyKey)) {
            conflict("A transfer with this idempotency key is already being processed")
        }
        try {
            // Re-check under lock in case of a concurrent request finishing first.
            transactionRepository.findByIdempotencyKey(idempotencyKey)?.let {
                return TransactionResponseData.from(it)
            }
            return executeTransfer(userId, request, idempotencyKey, stepUpCode, ip, deviceFingerprint)
        } finally {
            idempotencyLockStore.unlock(idempotencyKey)
        }
    }

    private fun executeTransfer(
        userId: UUID,
        request: TransferRequest,
        idempotencyKey: String,
        stepUpCode: String?,
        ip: String?,
        deviceFingerprint: String?
    ): TransactionResponseData {
        val type = try {
            TransactionType.valueOf(request.type.uppercase())
        } catch (e: IllegalArgumentException) {
            badRequest("Invalid transaction type: ${request.type}")
        }
        val amount = AmountFormat.parse(request.amount)
        if (amount <= BigDecimal.ZERO) badRequest("Amount must be positive")

        val sourceAccount = accountService.findOwnedAccount(userId, UUID.fromString(request.sourceAccountId))
        if (sourceAccount.status != AccountStatus.ACTIVE) unprocessable("Source account is not active")

        // FR-BE-012: "Step-up auth token required for transactions above threshold" — enforced server-side
        // regardless of client behavior (README §5.4 note / FR-AND-011). A real TOTP code (X-Step-Up-Code
        // header) is required and verified here, not merely trusted from a client-supplied boolean flag.
        val stepUpVerified = if (amount >= props.security.stepUpTransferThreshold) {
            verifyStepUp(userId, stepUpCode)
            true
        } else {
            false
        }

        var destinationAccount: Account? = null
        if (type == TransactionType.TRANSFER_INTERNAL) {
            val destId = request.destinationAccountId ?: badRequest("destination_account_id is required for TRANSFER_INTERNAL")
            destinationAccount = accountRepository.findById(UUID.fromString(destId)).orElseThrow { notFound("Destination account not found") }
            if (destinationAccount.id == sourceAccount.id) badRequest("Cannot transfer to the same account")
        } else if (type == TransactionType.TRANSFER_EXTERNAL) {
            if (request.destinationAccountNumber.isNullOrBlank()) badRequest("destination_account_number is required for TRANSFER_EXTERNAL")
        }

        if (sourceAccount.availableBalance < amount) unprocessable("Insufficient balance")

        // Spending limits and velocity rules (FR-BE-012 / MOD-013 Velocity Check).
        val debitedToday = transactionRepository.sumDebitsSince(sourceAccount.id, Instant.now().minusSeconds(86400))
        if (debitedToday.add(amount) > sourceAccount.dailyTransferLimit) {
            unprocessable("Daily transfer limit exceeded")
        }

        val anomalyScore = computeAnomalyScore(sourceAccount, amount, debitedToday)

        val transaction = Transaction(
            idempotencyKey = idempotencyKey,
            sourceAccountId = sourceAccount.id,
            destinationAccountId = destinationAccount?.id,
            destinationAccountNumber = if (type == TransactionType.TRANSFER_EXTERNAL) request.destinationAccountNumber else destinationAccount?.accountNumber,
            type = type,
            amount = amount,
            currency = request.currency,
            status = TransactionStatus.PENDING,
            reference = ReferenceGenerator.generate(),
            description = request.description,
            anomalyScore = anomalyScore,
            initiatedBy = InitiatedBy.USER,
            ipAddress = ip,
            deviceFingerprint = deviceFingerprint,
            userId = userId,
            stepUpVerified = stepUpVerified
        )

        sourceAccount.balance = sourceAccount.balance.subtract(amount)
        sourceAccount.availableBalance = sourceAccount.availableBalance.subtract(amount)
        sourceAccount.updatedAt = Instant.now()
        accountRepository.save(sourceAccount)

        if (destinationAccount != null) {
            destinationAccount.balance = destinationAccount.balance.add(amount)
            destinationAccount.availableBalance = destinationAccount.availableBalance.add(amount)
            destinationAccount.updatedAt = Instant.now()
            accountRepository.save(destinationAccount)
        }

        transaction.status = TransactionStatus.COMPLETED
        transaction.settledAt = Instant.now()
        transactionRepository.save(transaction)

        notificationService.notify(
            userId, "Transfer completed",
            "Your transfer of ${transaction.currency} has been completed. Ref: ${transaction.reference}",
            NotificationType.TRANSACTION, transaction.id.toString()
        )
        destinationAccount?.let {
            notificationService.notify(
                it.userId, "Funds received",
                "You received a transfer. Ref: ${transaction.reference}",
                NotificationType.TRANSACTION, transaction.id.toString()
            )
        }

        auditService.record(
            eventType = "TRANSFER", userId = userId, ipAddress = ip, deviceFingerprint = deviceFingerprint,
            eventData = mapOf(
                "transaction_id" to transaction.id.toString(),
                "type" to type.name,
                "anomaly_score" to anomalyScore
            )
        )

        return TransactionResponseData.from(transaction)
    }

    /** MOD-012 Anomaly Detection: naive heuristic scoring stand-in (0.0-1.0), not an ML model. */
    private fun computeAnomalyScore(account: Account, amount: BigDecimal, debitedToday: BigDecimal): Float {
        var score = 0f
        val limit = account.dailyTransferLimit
        if (limit > BigDecimal.ZERO) {
            val ratio = amount.divide(limit, 4, java.math.RoundingMode.HALF_UP).toFloat()
            score += (ratio * 0.6f).coerceAtMost(0.6f)
        }
        if (amount >= props.security.stepUpTransferThreshold) score += 0.2f
        if (debitedToday > BigDecimal.ZERO) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    /** FR-BE-012 step-up auth: requires a valid TOTP code from the user's enrolled MFA secret. */
    private fun verifyStepUp(userId: UUID, stepUpCode: String?) {
        val user = userRepository.findById(userId).orElseThrow { notFound("User not found") }
        if (!user.mfaEnabled || user.mfaSecretEncrypted == null) {
            unprocessable("MFA enrollment is required before transfers of this amount can be made")
        }
        if (stepUpCode.isNullOrBlank()) {
            unprocessable("Step-up authentication required: provide X-Step-Up-Code header with a valid MFA code")
        }
        val code = stepUpCode.toIntOrNull() ?: unprocessable("Invalid step-up code format")
        val secret = aesEncryptor.decrypt(user.mfaSecretEncrypted!!)
        if (!totp.authorize(secret, code)) {
            auditService.record(eventType = "STEP_UP_MFA_FAILED", userId = userId, result = AuditResult.FAILURE)
            unauthorized("Invalid step-up MFA code")
        }
        auditService.record(eventType = "STEP_UP_MFA_VERIFIED", userId = userId)
    }

    fun getTransaction(userId: UUID, transactionId: UUID): TransactionResponseData {
        val transaction = findOwnedTransaction(userId, transactionId)
        return TransactionResponseData.from(transaction)
    }

    fun search(
        userId: UUID, accountId: UUID?, type: String?, status: String?,
        startDate: String?, endDate: String?, page: Int, pageSize: Int
    ): PaginatedData<TransactionResponseData> {
        val typeEnum = type?.let { TransactionType.valueOf(it.uppercase()) }
        val statusEnum = status?.let { TransactionStatus.valueOf(it.uppercase()) }
        val start = startDate?.let { Instant.parse(it) }
        val end = endDate?.let { Instant.parse(it) }
        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = transactionRepository.search(userId, accountId, typeEnum, statusEnum, start, end, pageable)
        return PaginatedData(
            items = result.content.map { TransactionResponseData.from(it) },
            page = page, pageSize = pageSize,
            totalCount = result.totalElements, totalPages = result.totalPages
        )
    }

    /** FR-BE-015: only PENDING transactions within 10-minute window can be cancelled. */
    @Transactional
    fun cancel(userId: UUID, transactionId: UUID): TransactionResponseData {
        val transaction = findOwnedTransaction(userId, transactionId)
        if (transaction.status != TransactionStatus.PENDING) {
            unprocessable("Only PENDING transactions can be cancelled")
        }
        val windowEnd = transaction.createdAt.plusSeconds(props.security.cancelWindowMinutes * 60)
        if (Instant.now().isAfter(windowEnd)) {
            unprocessable("Cancel window has expired")
        }

        val sourceAccount = accountRepository.findById(transaction.sourceAccountId).orElseThrow { notFound("Source account not found") }
        sourceAccount.balance = sourceAccount.balance.add(transaction.amount)
        sourceAccount.availableBalance = sourceAccount.availableBalance.add(transaction.amount)
        accountRepository.save(sourceAccount)

        transaction.status = TransactionStatus.CANCELLED
        transaction.cancelledAt = Instant.now()
        transactionRepository.save(transaction)

        auditService.record(eventType = "TRANSACTION_CANCELLED", userId = userId, eventData = mapOf("transaction_id" to transactionId.toString()))
        return TransactionResponseData.from(transaction)
    }

    /** FR-BE-016: generate signed, single-use QR token (JWT, 60-second TTL). */
    fun generateQrToken(userId: UUID, request: QrGenerateRequest): QrTokenResponseData {
        val account = accountService.findOwnedAccount(userId, UUID.fromString(request.accountId))
        val amount = AmountFormat.parse(request.amount)
        if (amount <= BigDecimal.ZERO) badRequest("Amount must be positive")

        val token = jwtService.issueQrToken(userId, account.id, AmountFormat.toWire(amount), request.description)
        val expiresAt = Instant.now().plusSeconds(props.security.qrTokenTtlSeconds)
        return QrTokenResponseData(
            qrToken = token,
            expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiresAt),
            amount = AmountFormat.toWire(amount)
        )
    }

    /** FR-BE-017: validate signature, single-use enforcement, atomic consumption (ATK-011 defense). */
    @Transactional
    fun processQrPayment(userId: UUID, request: QrProcessRequest, ip: String?, deviceFingerprint: String?): TransactionResponseData {
        val decoded = try {
            jwtService.decode(request.qrToken, TokenType.QR)
        } catch (e: JwtException) {
            unauthorized("Invalid or expired QR token")
        }
        if (!qrTokenConsumptionStore.tryConsume(decoded.jti)) {
            conflict("QR token has already been used")
        }

        val payeeAccountId = decoded.extra["account_id"] as? String ?: badRequest("Malformed QR token")
        val amount = decoded.extra["amount"] as? String ?: badRequest("Malformed QR token")
        val description = decoded.extra["description"] as? String

        val transferRequest = TransferRequest(
            sourceAccountId = request.sourceAccountId,
            destinationAccountId = payeeAccountId,
            amount = amount,
            description = description ?: "QR payment",
            type = TransactionType.QR_PAYMENT.name,
            idempotencyKey = "qr-${decoded.jti}"
        )
        return transfer(userId, transferRequest, transferRequest.idempotencyKey, stepUpCode = null, ip = ip, deviceFingerprint = deviceFingerprint)
    }

    /** FRD §6.5 "Users can flag a transaction as fraudulent" / FR-BE-023 fraud case intake. */
    @Transactional
    fun reportFraud(userId: UUID, request: FraudReportRequest): FraudCase {
        val transaction = findOwnedTransaction(userId, UUID.fromString(request.transactionId))
        val fraudType = try {
            FraudType.valueOf(request.fraudType.uppercase())
        } catch (e: IllegalArgumentException) {
            badRequest("Invalid fraud_type: ${request.fraudType}")
        }
        val fraudCase = FraudCase(
            transactionId = transaction.id,
            reportedByUserId = userId,
            fraudType = fraudType,
            description = request.description
        )
        fraudCaseRepository.save(fraudCase)

        // FRD §6.5: "card auto-frozen (optional)" — freeze any card tied to the source account.
        cardRepository.findByAccountId(transaction.sourceAccountId).forEach { card ->
            if (card.status == CardStatus.ACTIVE) {
                card.status = CardStatus.FROZEN
                cardRepository.save(card)
            }
        }

        notificationService.notify(
            userId, "Fraud report received",
            "We've received your fraud report for transaction ${transaction.reference} and are investigating.",
            NotificationType.SECURITY, fraudCase.id.toString()
        )
        auditService.record(
            eventType = "FRAUD_REPORTED", userId = userId,
            eventData = mapOf("transaction_id" to transaction.id.toString(), "fraud_type" to fraudType.name)
        )
        return fraudCase
    }

    private fun findOwnedTransaction(userId: UUID, transactionId: UUID): Transaction {
        val transaction = transactionRepository.findById(transactionId).orElseThrow { notFound("Transaction not found") }
        if (transaction.userId != userId) forbidden("You do not have access to this transaction")
        return transaction
    }
}
