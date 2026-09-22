package com.securebank.backend.config

import com.securebank.backend.security.JwtAuthFilter
import com.securebank.backend.security.RequestSignatureFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val requestSignatureFilter: RequestSignatureFilter,
    private val props: SecureBankProperties
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(props.security.bcryptStrength)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val base = props.api.basePath
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "$base/auth/**",
                        "/h2-console/**",
                        "/actuator/health"
                    ).permitAll()
                    .requestMatchers("$base/admin/**").hasRole("ADMIN")
                    .requestMatchers("$base/security/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .headers { it.frameOptions { frame -> frame.sameOrigin() } }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = "application/json"
                    response.writer.write(
                        """{"status":"error","code":401,"message":"Authentication required","data":null}"""
                    )
                }
                ex.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = "application/json"
                    response.writer.write(
                        """{"status":"error","code":403,"message":"Insufficient permissions","data":null}"""
                    )
                }
            }
            .addFilterBefore(requestSignatureFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsFilter(): org.springframework.web.filter.CorsFilter {
        return org.springframework.web.filter.CorsFilter(corsConfigurationSource())
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf("https://dashboard-super-bank.vercel.app")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
