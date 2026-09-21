package com.securebank.backend.security

import com.securebank.backend.repository.UserRepository
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * FR-BE-003: validates the RS256 access token from the Authorization header
 * and populates the Spring Security context. Public endpoints (register,
 * login, etc.) are excluded via SecurityConfig permit-all rules, so a missing
 * or invalid token there simply leaves the context unauthenticated.
 */
@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ").trim()
            try {
                val decoded = jwtService.decode(token, TokenType.ACCESS)
                val user = userRepository.findById(decoded.userId).orElse(null)
                if (user != null) {
                    val principal = UserPrincipal.from(user)
                    val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
                    auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = auth
                    request.setAttribute("jti", decoded.jti)
                }
            } catch (_: JwtException) {
                // leave unauthenticated; entry point / access-denied handling covers this
            }
        }
        filterChain.doFilter(request, response)
    }
}
