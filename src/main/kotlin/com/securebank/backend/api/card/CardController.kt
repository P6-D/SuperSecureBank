package com.securebank.backend.api.card

import com.securebank.backend.common.ApiResponse
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.CardService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("\${securebank.api.base-path}/cards")
class CardController(private val cardService: CardService) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal, @RequestParam(required = false) accountId: UUID?): ApiResponse<List<CardResponseData>> {
        return ApiResponse.success(cardService.list(principal.id, accountId))
    }

    @PostMapping("/virtual")
    fun issueVirtualCard(@AuthenticationPrincipal principal: UserPrincipal, @Valid @RequestBody request: IssueCardRequest): ApiResponse<CardResponseData> {
        return ApiResponse.success(cardService.issueVirtualCard(principal.id, request), 201, "Virtual card issued")
    }

    @PatchMapping("/{cardId}/freeze")
    fun freeze(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable cardId: UUID): ApiResponse<CardResponseData> {
        return ApiResponse.success(cardService.freeze(principal.id, cardId), 200, "Card frozen")
    }

    @PatchMapping("/{cardId}/unfreeze")
    fun unfreeze(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable cardId: UUID): ApiResponse<CardResponseData> {
        return ApiResponse.success(cardService.unfreeze(principal.id, cardId), 200, "Card unfrozen")
    }

    @PatchMapping("/{cardId}/limits")
    fun updateLimits(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable cardId: UUID,
        @Valid @RequestBody request: CardLimitsRequest
    ): ApiResponse<CardResponseData> {
        return ApiResponse.success(cardService.updateLimits(principal.id, cardId, request), 200, "Card limits updated")
    }
}
