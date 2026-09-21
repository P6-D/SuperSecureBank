package com.securebank.backend.api.auth

import com.securebank.backend.domain.entity.User
import jakarta.validation.constraints.NotBlank
import java.time.format.DateTimeFormatter

data class RegisterRequest(
    @field:NotBlank val fullName: String,
    @field:NotBlank val email: String,
    @field:NotBlank val phone: String,
    @field:NotBlank val nationalId: String,
    @field:NotBlank val password: String,
    val deviceFingerprint: String? = null
)

data class RegisterResponseData(
    val userId: String,
    val verificationToken: String
)

data class VerifyOtpRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val otp: String
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    val deviceFingerprint: String? = null,
    val deviceName: String? = null,
    val deviceOs: String? = null,
    val deviceOsVersion: String? = null,
    val appVersion: String? = null
)

data class RefreshTokenRequest(
    @field:NotBlank val refreshToken: String
)

data class MfaVerifyRequest(
    @field:NotBlank val token: String,
    @field:NotBlank val mfaCode: String
)

data class MfaEnrollRequest(
    @field:NotBlank val password: String
)

data class MfaDisableRequest(
    @field:NotBlank val password: String
)

data class LoginResponseData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val mfaRequired: Boolean = false,
    val mfaToken: String? = null,
    val user: UserResponseData? = null
)

data class MfaEnrollResponseData(
    val secret: String,
    val qrCodeUri: String
)

data class UserResponseData(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val status: String,
    val mfaEnabled: Boolean,
    val lastLoginAt: String?,
    val lastLoginIp: String?,
    val createdAt: String
) {
    companion object {
        fun from(user: User) = UserResponseData(
            id = user.id.toString(),
            fullName = user.fullName,
            email = user.email,
            phone = user.phone,
            status = user.status.name,
            mfaEnabled = user.mfaEnabled,
            lastLoginAt = user.lastLoginAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
            lastLoginIp = user.lastLoginIp,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(user.createdAt)
        )
    }
}

data class DeviceSessionResponseData(
    val id: String,
    val deviceFingerprint: String,
    val deviceName: String?,
    val deviceOs: String?,
    val deviceOsVersion: String?,
    val appVersion: String?,
    val ipAddress: String?,
    val isTrusted: Boolean,
    val lastActiveAt: String,
    val createdAt: String
) {
    companion object {
        fun from(session: com.securebank.backend.domain.entity.DeviceSession) = DeviceSessionResponseData(
            id = session.id.toString(),
            deviceFingerprint = session.deviceFingerprint,
            deviceName = session.deviceName,
            deviceOs = session.deviceOs,
            deviceOsVersion = session.deviceOsVersion,
            appVersion = session.appVersion,
            ipAddress = session.ipAddress,
            isTrusted = session.isTrusted,
            lastActiveAt = DateTimeFormatter.ISO_INSTANT.format(session.lastActiveAt),
            createdAt = DateTimeFormatter.ISO_INSTANT.format(session.createdAt)
        )
    }
}
