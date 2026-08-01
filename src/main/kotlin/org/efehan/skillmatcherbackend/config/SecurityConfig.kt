package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.config.filter.JwtAuthenticationFilter
import org.efehan.skillmatcherbackend.config.filter.RateLimitingFilter
import org.efehan.skillmatcherbackend.config.properties.ActuatorProperties
import org.efehan.skillmatcherbackend.config.properties.CorsProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.time.Clock

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    @Lazy private val jwtAuthFilter: JwtAuthenticationFilter,
    private val rateLimitingFilter: RateLimitingFilter,
    private val corsProperties: CorsProperties,
) {
    /**
     * Metrics expose endpoint names, user counts and JVM internals, so the scrape needs a
     * credential of its own — the app's JWT cookies are useless to Prometheus. Health stays
     * open for container probes; it reports status only.
     */
    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(
        http: HttpSecurity,
        actuatorProperties: ActuatorProperties,
    ): SecurityFilterChain =
        http
            .securityMatcher("/actuator/**")
            // stateless basic auth, no cookie to ride on
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health/**").permitAll()
                it.anyRequest().authenticated()
            }.httpBasic { }
            .authenticationManager(
                ProviderManager(
                    DaoAuthenticationProvider(
                        InMemoryUserDetailsManager(
                            User
                                .withUsername(actuatorProperties.username)
                                .password(passwordEncoder().encode(actuatorProperties.password))
                                .authorities("SCRAPE")
                                .build(),
                        ),
                    ).apply { setPasswordEncoder(passwordEncoder()) },
                ),
            ).build()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { csrf ->
                csrf.spa()
                // STOMP handshake has no way to send the X-XSRF-TOKEN header
                csrf.ignoringRequestMatchers("/ws", "/ws/**")
                // CsrfAuthenticationStrategy would wipe the XSRF-TOKEN cookie on every
                // filter-authenticated request — session fixation defense, meaningless when STATELESS
                csrf.sessionAuthenticationStrategy { _, _, _ -> }
            }.cors { }
            .headers { headers ->
                // HSTS deliberately omitted: dev runs on plain HTTP, enable with TLS
                headers
                    .contentTypeOptions { }
                    .frameOptions { it.deny() }
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
                    .contentSecurityPolicy {
                        it.policyDirectives(
                            "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'",
                        )
                    }
            }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }.authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/csrf",
                        "/api/auth/invitations/validate",
                        "/api/auth/invitations/accept",
                        "/api/auth/password-reset/**",
                        "/api/public/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/ws",
                        "/ws/**",
                    ).permitAll()
                it.requestMatchers("/api/admin/**").hasRole("ADMIN")
                it.requestMatchers("/api/superadmin/**").hasRole("SUPERADMIN")
                it.anyRequest().authenticated()
            }.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600
            }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
