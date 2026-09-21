package com.securebank.backend.api.auth

import com.securebank.backend.common.ApiResponse
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("\${securebank.api.base-path}/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest, http: HttpServletRequest): ApiResponse<RegisterResponseData> {
        val data = authService.register(request, clientIp(http))
        return ApiResponse.success(data, 201, "Registration successful, please verify your account")
    }

    @PostMapping("/verify")
    fun verify(@Valid @RequestBody request: VerifyOtpRequest): ApiResponse<Unit> {
        authService.verify(request)
        return ApiResponse.success(Unit, 200, "Account verified")
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): ApiResponse<LoginResponseData> {
        val data = authService.login(request, clientIp(http))
        val message = if (data.mfaRequired) "MFA verification required" else "Login successful"
        return ApiResponse.success(data, 200, message)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest, http: HttpServletRequest): ApiResponse<LoginResponseData> {
        val data = authService.refresh(request, clientIp(http))
        return ApiResponse.success(data, 200, "Token refreshed")
    }

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestBody(required = false) request: RefreshTokenRequest?,
        http: HttpServletRequest
    ): ApiResponse<Unit> {
        val jti = http.getAttribute("jti") as? String
        if (principal != null) {
            authService.logout(principal.id, jti, request?.refreshToken, clientIp(http))
        }
        return ApiResponse.success(Unit, 200, "Logged out")
    }

    @PostMapping("/mfa/enroll")
    fun enrollMfa(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: MfaEnrollRequest
    ): ApiResponse<MfaEnrollResponseData> {
        val data = authService.enrollMfa(principal.id, request)
        return ApiResponse.success(data, 200, "MFA enrollment initiated")
    }

    @PostMapping("/mfa/verify")
    fun verifyMfa(@Valid @RequestBody request: MfaVerifyRequest, http: HttpServletRequest): ApiResponse<LoginResponseData> {
        val data = authService.verifyMfa(request, clientIp(http))
        return ApiResponse.success(data, 200, "MFA verified, login complete")
    }

    @PostMapping("/mfa/disable")
    fun disableMfa(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: MfaDisableRequest
    ): ApiResponse<Unit> {
        authService.disableMfa(principal.id, request)
        return ApiResponse.success(Unit, 200, "MFA disabled")
    }

    @GetMapping("/devices")
    fun listDevices(@AuthenticationPrincipal principal: UserPrincipal): ApiResponse<List<DeviceSessionResponseData>> {
        return ApiResponse.success(authService.listDevices(principal.id))
    }

    @DeleteMapping("/devices/{deviceId}")
    fun revokeDevice(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable deviceId: UUID): ApiResponse<Unit> {
        authService.revokeDevice(principal.id, deviceId)
        return ApiResponse.success(Unit, 200, "Device session revoked")
    }

    private fun clientIp(http: HttpServletRequest): String =
        http.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim() ?: http.remoteAddr
}
