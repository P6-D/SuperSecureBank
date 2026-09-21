package com.securebank.backend.api.security

import com.securebank.backend.common.ApiResponse
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.SecurityPanelService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * FR-BE-024 to FR-BE-027 (FRD §7.6, Security Control Panel — Testbed-Specific).
 * README §5.7: no Android Retrofit interface consumes this; internal
 * tooling / red-team-blue-team dashboard only. `hasRole('ADMIN')` stands in
 * for FRD's "admin JWT + IP whitelist" / "DEV/STAGING/RED_TEAM environments"
 * restrictions, which have no infra to enforce in this single-node testbed.
 */
@RestController
@RequestMapping("\${securebank.api.base-path}/security")
@PreAuthorize("hasRole('ADMIN')")
class SecurityPanelController(private val securityPanelService: SecurityPanelService) {

    @GetMapping("/modules")
    fun listModules(): ApiResponse<List<SecurityModuleResponseData>> {
        return ApiResponse.success(securityPanelService.listModules())
    }

    /** FR-BE-024: Module Toggle API. */
    @PatchMapping("/modules/{moduleName}")
    fun toggleModule(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable moduleName: String,
        @RequestBody request: ModuleToggleRequest
    ): ApiResponse<SecurityModuleResponseData> {
        return ApiResponse.success(securityPanelService.toggleModule(principal.id, moduleName, request), 200, "Module updated")
    }

    /** FR-BE-025: Attack Simulation Trigger. */
    @PostMapping("/simulate")
    fun simulate(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: AttackSimulationRequest
    ): ApiResponse<AttackSimulationResultData> {
        return ApiResponse.success(securityPanelService.simulateAttack(principal.id, request), 200, "Simulation completed")
    }

    /**
     * FR-BE-026: Security Event Stream. FRD specifies SSE/WebSocket; this
     * backend exposes a polled snapshot of the most recent events instead —
     * true push-based streaming is a documented future enhancement (no
     * message broker / SSE emitter infra in this H2 + in-memory-cache-only
     * scope).
     */
    @GetMapping("/events")
    fun events(@RequestParam(defaultValue = "50") limit: Int): ApiResponse<List<SecurityEventData>> {
        return ApiResponse.success(securityPanelService.recentEvents(limit))
    }

    /** FR-BE-027: Vulnerability Scanner Hook. */
    @PostMapping("/scan/trigger")
    fun triggerScan(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody(required = false) request: ScanTriggerRequest?
    ): ApiResponse<ScanTriggerResponseData> {
        return ApiResponse.success(securityPanelService.triggerScan(principal.id, request ?: ScanTriggerRequest()), 202, "Scan triggered")
    }
}
