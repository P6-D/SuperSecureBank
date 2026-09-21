package com.securebank.backend.api.account

import com.securebank.backend.api.transaction.TransactionResponseData
import com.securebank.backend.common.ApiResponse
import com.securebank.backend.common.PaginatedData
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.AccountService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("\${securebank.api.base-path}/accounts")
class AccountController(private val accountService: AccountService) {

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest): ApiResponse<AccountResponseData> {
        return ApiResponse.success(accountService.createAccount(request), 201, "Account created")
    }

    @GetMapping
    fun listAccounts(@AuthenticationPrincipal principal: UserPrincipal): ApiResponse<List<AccountResponseData>> {
        return ApiResponse.success(accountService.listForUser(principal.id))
    }

    @GetMapping("/{accountId}")
    fun getAccount(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable accountId: UUID): ApiResponse<AccountResponseData> {
        return ApiResponse.success(accountService.getAccount(principal.id, accountId))
    }

    @GetMapping("/{accountId}/balance")
    fun getBalance(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable accountId: UUID,
        http: HttpServletRequest
    ): ApiResponse<AccountResponseData> {
        return ApiResponse.success(accountService.getBalance(principal.id, accountId, http.remoteAddr))
    }

    @GetMapping("/{accountId}/transactions")
    fun getStatement(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable accountId: UUID,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<PaginatedData<TransactionResponseData>> {
        return ApiResponse.success(
            accountService.getStatement(principal.id, accountId, startDate, endDate, page, pageSize)
        )
    }
}
