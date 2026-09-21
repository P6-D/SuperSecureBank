package com.securebank.backend.service

import com.securebank.backend.api.auth.*
import com.securebank.backend.cache.LoginAttemptStore
import com.securebank.backend.cache.OtpStore
import com.securebank.backend.common.*
import com.securebank.backend.config.SecureBankProperties
import com.securebank.backend.domain.entity.*
import com.securebank.backend.repository.DeviceSessionRepository
import com.securebank.backend.repository.UserRepository
import com.securebank.backend.security.AesEncryptor
import com.securebank.backend.security.JwtService
import com.securebank.backend.security.TokenType
import com.warrenstrange.googleauth.GoogleAuthenticator
import io.jsonwebtoken.JwtException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val deviceSessionRepository: DeviceSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val otpStore: OtpStore,
    private val loginAttemptStore: LoginAttemptStore,
    private val auditService: AuditService,
    private val notificationService: NotificationService,
    private val aesEncryptor: AesEncryptor,
    private val props: SecureBankProperties
) {
    private val totp = GoogleAuthenticator()

    @Transactional
    fun register(request: RegisterRequest, ip: String?): RegisterResponseData {
        if (!EmailValidator.isValid(request.email)) badRequest("Invalid email format")
        if (!PhoneValidator.isValid(request.phone)) badRequest("Invalid phone format")
        PasswordPolicy.validate(request.password)
        if (userRepository.existsByEmailIgnoreCase(request.email)) conflict("Email already registered")
        if (userRepository.existsByPhone(request.phone)) conflict("Phone already registered")

        val user = User(
            fullName = request.fullName,
            email = request.email.lowercase(),
            phone = request.phone,
            nationalId = request.nationalId,
            passwordHash = passwordEncoder.encode(request.password),
            status = UserStatus.PENDING_VERIFICATION
        )
        userRepository.save(user)

        val otp = OtpGenerator.generate(props.otp.length)
        otpStore.put(user.email, otp)
        // FR-BE-001: "Send OTP via SMS/email (simulated)" — logged instead of a real gateway integration.
        auditService.record(
            eventType = "REGISTER",
            userId = user.id,
            eventData = mapOf("email" to user.email, "simulated_otp" to otp),
            ipAddress = ip
        )

        val verificationToken = sha256Hex("${user.id}|${Instant.now().epochSecond}")
        return RegisterResponseData(userId = user.id.toString(), verificationToken = verificationToken)
    }

    @Transactional
    fun verify(request: VerifyOtpRequest) {
        val user = userRepository.findByEmailIgnoreCase(request.email) ?: notFound("User not found")
        if (!otpStore.verify(user.email, request.otp)) badRequest("Invalid or expired OTP")
        user.status = UserStatus.ACTIVE
        user.updatedAt = Instant.now()
        userRepository.save(user)
        auditService.record(eventType = "VERIFY_ACCOUNT", userId = user.id)
    }

    @Transactional
    fun login(request: LoginRequest, ip: String?): LoginResponseData {
        val attempts = loginAttemptStore.recordAttempt(ip ?: "unknown")
        if (attempts > props.security.maxFailedLoginAttempts * 3) {
            // MOD-002/MOD-003: coarse per-IP velocity guard ahead of the per-account lock below.
            rateLimited("Too many login attempts from this network, please try again later")
        }

        val user = userRepository.findByEmailIgnoreCase(request.email)
        // Always run a bcrypt comparison even on unknown user to reduce user-enumeration timing signal.
        val passwordMatches = if (user != null) {
            passwordEncoder.matches(request.password, user.passwordHash)
        } else {
            passwordEncoder.matches(request.password, DUMMY_HASH)
            false
        }

        if (user == null || !passwordMatches) {
            if (user != null) registerFailedAttempt(user)
            auditService.record(
                eventType = "LOGIN", eventData = mapOf("email" to request.email),
                ipAddress = ip, result = AuditResult.FAILURE
            )
            unauthorized("Invalid email or password")
        }

        when (user.status) {
            UserStatus.LOCKED -> forbidden("Account is locked due to repeated failed login attempts")
            UserStatus.SUSPENDED -> forbidden("Account is suspended")
            UserStatus.CLOSED -> forbidden("Account is closed")
            UserStatus.PENDING_VERIFICATION -> forbidden("Account is not verified yet")
            UserStatus.ACTIVE -> Unit
        }

        user.failedLoginAttempts = 0
        user.lastLoginAt = Instant.now()
        user.lastLoginIp = ip
        userRepository.save(user)

        auditService.record(
            eventType = "LOGIN", userId = user.id, ipAddress = ip,
            deviceFingerprint = request.deviceFingerprint,
            eventData = mapOf("device_name" to request.deviceName)
        )

        if (user.mfaEnabled) {
            val mfaToken = jwtService.issueMfaToken(user.id)
            return LoginResponseData(
                accessToken = "", refreshToken = "", expiresIn = 0,
                mfaRequired = true, mfaToken = mfaToken, user = null
            )
        }

        return issueSession(
            user, request.deviceFingerprint, request.deviceName,
            request.deviceOs, request.deviceOsVersion, request.appVersion, ip
        )
    }

    @Transactional
    fun verifyMfa(request: MfaVerifyRequest, ip: String?): LoginResponseData {
        val decoded = try {
            jwtService.decode(request.token, TokenType.MFA)
        } catch (e: JwtException) {
            unauthorized("Invalid or expired MFA session")
        }
        val user = userRepository.findById(decoded.userId).orElseThrow { notFound("User not found") }
        val secret = user.mfaSecretEncrypted?.let { aesEncryptor.decrypt(it) } ?: badRequest("MFA not enrolled")
        val code = request.mfaCode.toIntOrNull() ?: badRequest("Invalid MFA code format")
        if (!totp.authorize(secret, code)) {
            auditService.record(eventType = "MFA_VERIFY", userId = user.id, ipAddress = ip, result = AuditResult.FAILURE)
            unauthorized("Invalid MFA code")
        }
        auditService.record(eventType = "MFA_VERIFY", userId = user.id, ipAddress = ip)
        return issueSession(user, null, null, null, null, null, ip)
    }

    @Transactional
    fun enrollMfa(userId: UUID, request: MfaEnrollRequest): MfaEnrollResponseData {
        val user = userRepository.findById(userId).orElseThrow { notFound("User not found") }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) unauthorized("Invalid password")
        val credentials = totp.createCredentials()
        user.mfaSecretEncrypted = aesEncryptor.encrypt(credentials.key)
        user.mfaEnabled = true
        userRepository.save(user)
        val qrUri = "otpauth://totp/SecureBank:${user.email}?secret=${credentials.key}&issuer=SecureBank"
        auditService.record(eventType = "MFA_ENROLL", userId = user.id)
        return MfaEnrollResponseData(secret = credentials.key, qrCodeUri = qrUri)
    }

    @Transactional
    fun disableMfa(userId: UUID, request: MfaDisableRequest) {
        val user = userRepository.findById(userId).orElseThrow { notFound("User not found") }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) unauthorized("Invalid password")
        user.mfaEnabled = false
        user.mfaSecretEncrypted = null
        userRepository.save(user)
        auditService.record(eventType = "MFA_DISABLE", userId = user.id)
    }

    @Transactional
    fun refresh(request: RefreshTokenRequest, ip: String?): LoginResponseData {
        val decoded = try {
            jwtService.decode(request.refreshToken, TokenType.REFRESH)
        } catch (e: JwtException) {
            unauthorized("Invalid or expired refresh token")
        }
        val hash = sha256Hex(request.refreshToken)
        val session = deviceSessionRepository.findByRefreshTokenHash(hash)
        if (session == null || session.revokedAt != null) {
            // FR-BE-005: refresh token reuse detection -> revoke all sessions for this user.
            jwtService.revokeRefreshToken(decoded.jti)
            deviceSessionRepository.findByUserIdAndRevokedAtIsNull(decoded.userId).forEach {
                it.revokedAt = Instant.now()
                deviceSessionRepository.save(it)
            }
            auditService.record(
                eventType = "REFRESH_TOKEN_REUSE_DETECTED", userId = decoded.userId,
                ipAddress = ip, result = AuditResult.BLOCKED, securityModuleTriggered = "MOD-004"
            )
            unauthorized("Refresh token reuse detected, all sessions revoked")
        }

        val user = userRepository.findById(decoded.userId).orElseThrow { unauthorized("User not found") }
        jwtService.revokeRefreshToken(decoded.jti)

        val newAccessToken = jwtService.issueAccessToken(user.id)
        val newRefreshToken = jwtService.issueRefreshToken(user.id)
        session.refreshTokenHash = sha256Hex(newRefreshToken)
        session.lastActiveAt = Instant.now()
        deviceSessionRepository.save(session)

        return LoginResponseData(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            expiresIn = props.jwt.accessTokenTtlSeconds,
            mfaRequired = false,
            user = UserResponseData.from(user)
        )
    }

    fun logout(userId: UUID, jti: String?, refreshToken: String?, ip: String?) {
        jti?.let { jwtService.revokeAccessToken(it) }
        refreshToken?.let {
            val hash = sha256Hex(it)
            deviceSessionRepository.findByRefreshTokenHash(hash)?.let { session ->
                session.revokedAt = Instant.now()
                deviceSessionRepository.save(session)
            }
        }
        auditService.record(eventType = "LOGOUT", userId = userId, ipAddress = ip)
    }

    fun listDevices(userId: UUID): List<DeviceSessionResponseData> =
        deviceSessionRepository.findByUserIdAndRevokedAtIsNull(userId).map { DeviceSessionResponseData.from(it) }

    @Transactional
    fun revokeDevice(userId: UUID, deviceId: UUID) {
        val session = deviceSessionRepository.findById(deviceId).orElseThrow { notFound("Device session not found") }
        if (session.userId != userId) forbidden("Cannot revoke another user's device session")
        session.revokedAt = Instant.now()
        deviceSessionRepository.save(session)
        auditService.record(eventType = "DEVICE_REVOKED", userId = userId, eventData = mapOf("device_id" to deviceId.toString()))
    }

    private fun issueSession(
        user: User,
        deviceFingerprint: String?,
        deviceName: String?,
        deviceOs: String?,
        deviceOsVersion: String?,
        appVersion: String?,
        ip: String?
    ): LoginResponseData {
        val accessToken = jwtService.issueAccessToken(user.id)
        val refreshToken = jwtService.issueRefreshToken(user.id)

        val session = DeviceSession(
            userId = user.id,
            deviceFingerprint = deviceFingerprint ?: "unknown",
            deviceName = deviceName,
            deviceOs = deviceOs,
            deviceOsVersion = deviceOsVersion,
            appVersion = appVersion,
            refreshTokenHash = sha256Hex(refreshToken),
            ipAddress = ip,
            isTrusted = false
        )
        deviceSessionRepository.save(session)

        notificationService.notify(
            user.id, "Welcome back", "New sign-in detected on your account.",
            NotificationType.SECURITY, session.id.toString()
        )

        return LoginResponseData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = props.jwt.accessTokenTtlSeconds,
            mfaRequired = false,
            user = UserResponseData.from(user)
        )
    }

    private fun registerFailedAttempt(user: User) {
        user.failedLoginAttempts += 1
        if (user.failedLoginAttempts >= props.security.maxFailedLoginAttempts) {
            user.status = UserStatus.LOCKED
            notificationService.notify(
                user.id, "Account locked",
                "Your account was locked after repeated failed login attempts.",
                NotificationType.SECURITY, null
            )
        }
        userRepository.save(user)
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        // Precomputed bcrypt hash of a random value, used to keep login timing similar for unknown emails.
        private const val DUMMY_HASH = "\$2a\$12\$abcdefghijklmnopqrstuuOZY6nB1r8fVYQ8f0v9wexi9y1o2h9Ki"
    }
}
