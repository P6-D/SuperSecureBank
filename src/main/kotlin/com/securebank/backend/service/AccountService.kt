package com.securebank.backend.service

import com.securebank.backend.api.account.AccountResponseData
import com.securebank.backend.api.account.CreateAccountRequest
import com.securebank.backend.api.transaction.TransactionResponseData
import com.securebank.backend.common.*
import com.securebank.backend.config.SecureBankProperties
import com.securebank.backend.domain.entity.Account
import com.securebank.backend.domain.entity.AccountStatus
import com.securebank.backend.domain.entity.AccountType
import com.securebank.backend.repository.AccountRepository
import com.securebank.backend.repository.TransactionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val auditService: AuditService,
    private val props: SecureBankProperties
) {
    /** FR-BE-008: only admin/internal service may create accounts (enforced at controller via hasRole ADMIN). */
    @Transactional
    fun createAccount(request: CreateAccountRequest): AccountResponseData {
        val type = try {
            AccountType.valueOf(request.accountType.uppercase())
        } catch (e: IllegalArgumentException) {
            badRequest("Invalid account_type: ${request.accountType}")
        }
        var accountNumber: String
        do {
            accountNumber = AccountNumberGenerator.generate()
        } while (accountRepository.existsByAccountNumber(accountNumber))

        val initial = AmountFormat.parse(request.initialBalance)
        val account = Account(
            userId = UUID.fromString(request.userId),
            accountNumber = accountNumber,
            accountType = type,
            currency = request.currency,
            balance = initial,
            availableBalance = initial
        )
        accountRepository.save(account)
        return AccountResponseData.from(account)
    }

    fun listForUser(userId: UUID): List<AccountResponseData> =
        accountRepository.findByUserId(userId).map { AccountResponseData.from(it) }

    fun getAccount(userId: UUID, accountId: UUID): AccountResponseData {
        val account = findOwnedAccount(userId, accountId)
        return AccountResponseData.from(account)
    }

    /** FR-BE-010: "Balance access logged to audit trail". */
    fun getBalance(userId: UUID, accountId: UUID, ip: String?): AccountResponseData {
        val account = findOwnedAccount(userId, accountId)
        auditService.record(
            eventType = "BALANCE_ACCESS", userId = userId,
            eventData = mapOf("account_id" to accountId.toString()), ipAddress = ip
        )
        return AccountResponseData.from(account)
    }

    /** FR-BE-011: statement date range must be server-enforced to max 12 months. */
    fun getStatement(
        userId: UUID, accountId: UUID, startDate: String?, endDate: String?, page: Int, pageSize: Int
    ): PaginatedData<TransactionResponseData> {
        findOwnedAccount(userId, accountId)

        val end = endDate?.let { Instant.parse(it) } ?: Instant.now()
        val maxRangeStart = end.minusSeconds(props.security.statementMaxRangeMonths * 30L * 24 * 3600)
        var start = startDate?.let { Instant.parse(it) } ?: maxRangeStart
        if (start.isBefore(maxRangeStart)) start = maxRangeStart
        if (start.isAfter(end)) badRequest("start_date must be before end_date")

        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = transactionRepository.findForAccountStatement(accountId, start, end, pageable)
        return PaginatedData(
            items = result.content.map { TransactionResponseData.from(it) },
            page = page, pageSize = pageSize,
            totalCount = result.totalElements, totalPages = result.totalPages
        )
    }

    fun findOwnedAccount(userId: UUID, accountId: UUID): Account {
        val account = accountRepository.findById(accountId).orElseThrow { notFound("Account not found") }
        // FR-BE-009 / ATK-012 IDOR defense: ownership check — users can only access their own accounts.
        if (account.userId != userId) forbidden("You do not have access to this account")
        return account
    }
}
