package com.securebank.backend.config

import com.securebank.backend.common.AccountNumberGenerator
import com.securebank.backend.domain.entity.*
import com.securebank.backend.repository.AccountRepository
import com.securebank.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Local-testbed convenience seeder: creates one ADMIN and one demo end-user
 * (with a funded CHECKING account) on first boot against the in-memory H2
 * database, so the Android app / curl / Postman have something to log in
 * against immediately without a manual bootstrap step. Not part of the FRD
 * spec — purely developer-experience for this local scope.
 */
@Component
class DataSeeder(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(DataSeeder::class.java)

    override fun run(vararg args: String?) {
        if (userRepository.count() > 0) return

        val admin = User(
            fullName = "System Admin",
            email = "admin@securebank.testbed",
            phone = "+10000000000",
            nationalId = "ADMIN-0000",
            passwordHash = passwordEncoder.encode("Admin@12345!"),
            status = UserStatus.ACTIVE,
            role = UserRole.ADMIN
        )
        userRepository.save(admin)

        val demoUser = User(
            fullName = "Demo User",
            email = "demo@securebank.testbed",
            phone = "+10000000001",
            nationalId = "DEMO-0001",
            passwordHash = passwordEncoder.encode("Demo@12345!"),
            status = UserStatus.ACTIVE,
            role = UserRole.USER
        )
        userRepository.save(demoUser)

        val checking = Account(
            userId = demoUser.id,
            accountNumber = AccountNumberGenerator.generate(),
            accountType = AccountType.CHECKING,
            currency = "USD",
            balance = BigDecimal("5000.0000"),
            availableBalance = BigDecimal("5000.0000")
        )
        accountRepository.save(checking)

        log.info(
            "Seeded testbed data: admin=admin@securebank.testbed/Admin@12345!, " +
                "demo=demo@securebank.testbed/Demo@12345! (account {})",
            checking.accountNumber
        )
    }
}
