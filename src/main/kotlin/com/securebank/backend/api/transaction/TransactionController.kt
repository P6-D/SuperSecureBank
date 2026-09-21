package com.securebank.backend.api.transaction

import com.securebank.backend.common.ApiResponse
import com.securebank.backend.common.PaginatedData
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.TransactionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("\${securebank.api.base-path}/transactions")
class TransactionController(private val transactionService: TransactionService) {

    @PostMapping("/transfer")
    fun transfer(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: TransferRequest,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestHeader("X-Step-Up-Code", required = false) stepUpCode: String?,
        http: HttpServletRequest
    ): ApiResponse<TransactionResponseData> {
        val data = transactionService.transfer(
            principal.id, request, idempotencyKey, stepUpCode,
            clientIp(http), http.getHeader("X-Device-Fingerprint")
        )
        return ApiResponse.success(data, 201, "Transfer processed")
    }

    @GetMapping("/{transactionId}")
    fun getTransaction(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable transactionId: UUID): ApiResponse<TransactionResponseData> {
        return ApiResponse.success(transactionService.getTransaction(principal.id, transactionId))
    }

    @GetMapping
    fun search(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) accountId: UUID?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<PaginatedData<TransactionResponseData>> {
        return ApiResponse.success(
            transactionService.search(principal.id, accountId, type, status, startDate, endDate, page, pageSize)
        )
    }

    @DeleteMapping("/{transactionId}")
    fun cancel(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable transactionId: UUID): ApiResponse<TransactionResponseData> {
        return ApiResponse.success(transactionService.cancel(principal.id, transactionId), 200, "Transaction cancelled")
    }

    @PostMapping("/qr/generate")
    fun generateQr(@AuthenticationPrincipal principal: UserPrincipal, @Valid @RequestBody request: QrGenerateRequest): ApiResponse<QrTokenResponseData> {
        return ApiResponse.success(transactionService.generateQrToken(principal.id, request), 201, "QR token generated")
    }

    @PostMapping("/qr/process")
    fun processQr(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: QrProcessRequest,
        http: HttpServletRequest
    ): ApiResponse<TransactionResponseData> {
        val data = transactionService.processQrPayment(principal.id, request, clientIp(http), http.getHeader("X-Device-Fingerprint"))
        return ApiResponse.success(data, 201, "QR payment processed")
    }

    private fun clientIp(http: HttpServletRequest): String =
        http.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim() ?: http.remoteAddr
}
