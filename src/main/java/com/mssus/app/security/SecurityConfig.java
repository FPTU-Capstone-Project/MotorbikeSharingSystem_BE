package com.mssus.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.exception.catalog.ErrorCatalogService;
import com.mssus.app.common.exception.catalog.ErrorEntry;
import com.mssus.app.dto.response.ErrorDetail;
import com.mssus.app.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final ObjectMapper objectMapper;
    private final ErrorCatalogService errorCatalogService;

    public static class SecurityEndpoints {

        // Endpoints that don't require authentication
        public static final String[] PUBLIC_PATHS = {
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/users/forgot-password",
                "/api/v1/otp/**",
                "/error",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/swagger-resources/**",
                "/webjars/**",
                "/actuator/health",
                "/debug/throw-test",
                "/debug/catalog-test",
                "/api/v1/otp",
                "/api/v1/auth/refresh",
                // TODO: Remove in production - Development only
                "/api/v1/reports/**"  // Temporarily public for frontend development
        };

        // Endpoints requiring any authentication (general authenticated users)
        public static final String[] PRIVATE_PATHS = {
                "/api/v1/auth/logout"
        };

        // Endpoints requiring ADMIN role
        public static final String[] ADMIN_PATHS = {
                "/api/v1/accounts/**",
                "/api/v1/admin/wallet/**",
                "/api/v1/verification/students/pending",
                "/api/v1/verification/students/{id}",
                "/api/v1/verification/students/{id}/approve",
                "/api/v1/verification/students/{id}/reject",
                "/api/v1/verification/students/history",
                "/api/v1/verification/students/bulk-approve",
                "/api/v1/verification/drivers/pending",
                "/api/v1/verification/drivers/{id}/kyc",
                "/api/v1/verification/drivers/{id}/approve-docs",
                "/api/v1/verification/drivers/{id}/approve-license",
                "/api/v1/verification/drivers/{id}/approve-vehicle",
                "/api/v1/verification/drivers/{id}/reject",
                "/api/v1/verification/drivers/{id}/background-check",
                "/api/v1/verification/drivers/stats"
        };

        // Reports endpoints - ADMIN or ANALYST role
        public static final String[] REPORTS_PATHS = {
                "/api/v1/reports/**"
        };

        // Endpoints for authenticated users (profile management)
        public static final String[] USER_PATHS = {
                "/api/v1/me/**",
                "/api/v1/me",
                "/api/v1/me/update-password",
                "/api/v1/me/switch-profile",
                "/api/v1/me/update-avatar",
                "/api/v1/me/student-verifications",
                "/api/v1/users/reset-password",
        };

        // Endpoints specific to riders (currently none identified)
        public static final String[] RIDER_PATHS = {
                "/api/v1/me/rider-verifications"
        };

        // Endpoints specific to drivers
        public static final String[] DRIVER_PATHS = {
                "/api/v1/me/driver-verifications",
                "/api/v1/vehicles/**",
                "/api/v1/vehicles",
                "/api/v1/vehicles/{vehicleId}",
                "/api/v1/vehicles/driver/{driverId}",
                "/api/v1/vehicles/status/{status}"
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("Access denied for URI: {}", request.getRequestURI());

                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

                            ErrorEntry errorEntry = errorCatalogService.getErrorEntry("auth.unauthorized.access-denied");

                            ErrorDetail errorDetail = ErrorDetail.builder()
                                    .id("auth.unauthorized.access-denied")
                                    .message(errorEntry.getMessageTemplate())
                                    .domain(errorEntry.getDomain())
                                    .category(errorEntry.getCategory())
                                    .severity(errorEntry.getSeverity())
                                    .retryable(errorEntry.getIsRetryable())
                                    .build();

                            ErrorResponse errorResponse = ErrorResponse.builder()
                                    .traceId(UUID.randomUUID().toString())
                                    .error(errorDetail)
                                    .timestamp(LocalDateTime.now())
                                    .build();

                            try {
                                objectMapper.writeValue(response.getOutputStream(), errorResponse);
                            } catch (IOException e) {
                                log.error("Error writing access denied response", e);
                            }
                        }))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers(SecurityEndpoints.PUBLIC_PATHS).permitAll()
//
//                        // Admin-only endpoints
//                        .requestMatchers(SecurityEndpoints.ADMIN_PATHS).hasRole("ADMIN")
//
//                        // Reports endpoints - ADMIN or STAFF role
//                        .requestMatchers(SecurityEndpoints.REPORTS_PATHS).hasAnyRole("ADMIN", "STAFF")
//
//                        // Rider-specific endpoints - currently none
//                        .requestMatchers(SecurityEndpoints.RIDER_PATHS).hasRole("RIDER")
//
//                        // Driver-specific endpoints
//                        .requestMatchers(SecurityEndpoints.DRIVER_PATHS).hasRole("DRIVER")
//
//                        // User profile endpoints - any authenticated user
//                        .requestMatchers(SecurityEndpoints.USER_PATHS).authenticated()
//
//                        // Private endpoints - any authenticated user
//                        .requestMatchers(SecurityEndpoints.PRIVATE_PATHS).authenticated()

                        .anyRequest().permitAll()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // Configure from properties in production
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
