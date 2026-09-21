package com.securebank.backend.api.transaction

import com.securebank.backend.common.ApiResponse
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.TransactionService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * FRD §6.5 / README §5.4: `POST /fraud-reports` is a top-level path (not
 * nested under /transactions) exactly as consumed by
 * `TransactionApiService.reportFraud()`.
 */
@RestController
@RequestMapping("\${securebank.api.base-path}/fraud-reports")
class FraudReportController(private val transactionService: TransactionService) {

    @PostMapping
    fun reportFraud(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: FraudReportRequest
    ): ApiResponse<Unit> {
        transactionService.reportFraud(principal.id, request)
        return ApiResponse.success(Unit, 201, "Fraud report submitted")
    }
}
