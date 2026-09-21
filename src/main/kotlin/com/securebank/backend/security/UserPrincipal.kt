package com.securebank.backend.security

import com.securebank.backend.domain.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

class UserPrincipal(
    val id: UUID,
    private val emailValue: String,
    private val role: String
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_$role"))
    override fun getPassword(): String? = null
    override fun getUsername(): String = emailValue

    companion object {
        fun from(user: User) = UserPrincipal(user.id, user.email, user.role.name)
    }
}
