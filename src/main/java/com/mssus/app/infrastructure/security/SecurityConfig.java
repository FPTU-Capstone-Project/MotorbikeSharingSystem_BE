package com.mssus.app.infrastructure.security;

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
                // Auth endpoints
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/forgot-password",
                // OTP endpoints
                "/api/v1/otp/**",
                // Swagger/OpenAPI documentation
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/swagger-resources/**",
                "/webjars/**",
                // Health check
                "/actuator/health",
                // Error handling
                "/error",
                // Debug endpoints (remove in production)
                "/debug/throw-test",
                "/debug/catalog-test",
                // WebSocket/STOMP handshake endpoints
                "/ws",
                "/ws/**",
                "/ws-native",
                "/ws-native/**"
        };

        // Endpoints requiring ADMIN role
        public static final String[] ADMIN_PATHS = {
                // User management
                "/api/v1/admin/users/**",
                // Verification management
                "/api/v1/verification/**",
                // SOS alerts management (admin list and acknowledge)
                "/api/v1/sos/alerts/*/acknowledge",
                "/api/v1/sos/alerts",
                // Wallet payout processing (specific operations first)
                "/api/v1/wallet/payout/pending",
                "/api/v1/wallet/payout/*/process",
                "/api/v1/wallet/payout/*/complete",
                "/api/v1/wallet/payout/*/fail",
                // Refund management (admin operations - specific first)
                "/api/v1/refunds/requests/pending",
                "/api/v1/refunds/requests/approved",
                "/api/v1/refunds/requests/status/*",
                "/api/v1/refunds/requests/approve",
                "/api/v1/refunds/requests/reject",
                "/api/v1/refunds/requests/*/complete",
                "/api/v1/refunds/requests/*/failed",
                "/api/v1/refunds/requests/count/pending",
                "/api/v1/refunds/requests/user/*",
                // Profile management (admin view)
                "/api/v1/me/all",
                "/api/v1/vehicles/**",
                // User reports - Admin-only endpoints (specific paths, not wildcard)
                // Note: POST /api/v1/user-reports, GET /my-reports, POST /{reportId}/driver-response 
                // are in AUTHENTICATED_PATHS for regular users
                "/api/v1/user-reports/analytics",
                "/api/v1/user-reports/*/start-chat"
                // GET /api/v1/user-reports, GET /{reportId}, POST /{reportId}/resolve, PATCH /{reportId}
                // are protected by @PreAuthorize("hasRole('ADMIN')") in controller
        };

        // Endpoints requiring ADMIN or STAFF role
        public static final String[] ADMIN_STAFF_PATHS = {
                // Refund management (admin/staff)
                "/api/v1/refunds/requests/pending",
                "/api/v1/refunds/requests/approved",
                "/api/v1/refunds/requests/status/*",
                "/api/v1/refunds/requests/approve",
                "/api/v1/refunds/requests/reject",
                "/api/v1/refunds/requests/count/pending",
                // SOS resolve (admin/staff)
                "/api/v1/sos/alerts/*/resolve"
        };

        // Endpoints specific to RIDER role
        public static final String[] RIDER_PATHS = {
                // Create ride requests
                "/api/v1/ride-requests",
                "/api/v1/ride-requests/rides/*",
                // Browse available rides
                "/api/v1/rides/available",
                // Wallet top-up (specific operations)
                "/api/v1/wallet/topup/init",
                // Driver verification submission (rider can submit to become driver)
                "/api/v1/me/driver-verifications/license",
                "/api/v1/me/driver-verifications/documents",
                "/api/v1/me/driver-verifications/vehicle-registration"
        };

        // Endpoints specific to DRIVER role
        public static final String[] DRIVER_PATHS = {
                // Create/Manage rides (specific operations first)
                "/api/v1/rides/start-ride-request",
                "/api/v1/rides/complete-ride-request",
                "/api/v1/rides/*/start",
                "/api/v1/rides/*/complete",
                "/api/v1/rides/driver/*",
                "/api/v1/rides",
                // Ride requests (accept/reject)
                "/api/v1/ride-requests/broadcasting",
                "/api/v1/ride-requests/*/accept",
                "/api/v1/ride-requests/*/reject",
                "/api/v1/ride-requests/*/broadcast/accept",
                "/api/v1/ride-requests/rides/*",
                // Vehicle management
                "/api/v1/vehicles",
                "/api/v1/vehicles/*",
                "/api/v1/vehicles/driver",
                "/api/v1/vehicles/status/*",
                // Driver status
                "/api/v1/me/driver-status",
                // Driver earnings
                "/api/v1/wallet/earnings"
        };

        // Endpoints for authenticated users (any role)
        public static final String[] AUTHENTICATED_PATHS = {
                // Auth
                "/api/v1/auth/logout",
                // Profile management
                "/api/v1/me",
                "/api/v1/me/profile",
                "/api/v1/me/update-password",
                "/api/v1/me/switch-profile",
                "/api/v1/me/update-avatar",
                "/api/v1/me/student-verifications",
                // Wallet
                "/api/v1/wallet/balance",
                "/api/v1/wallet/payout/init",
                // SOS (user operations - specific first)
                "/api/v1/sos/alerts/me",
                "/api/v1/sos/alerts/*/timeline",
                "/api/v1/sos/alerts/*",
                "/api/v1/sos/alerts",
                "/api/v1/sos/contacts",
                "/api/v1/sos/contacts/*",
                "/api/v1/sos/contacts/*/primary",
                // Chat
                "/api/v1/chat/conversations",
                "/api/v1/chat/conversations/*/messages",
                "/api/v1/chat/conversations/read",
                "/api/v1/chat/unread-count",
                "/api/v1/chat/upload-image",
                "/api/v1/chat/messages",
                // User reports - User-accessible endpoints
                // POST /api/v1/user-reports - Submit report (any authenticated user)
                "/api/v1/user-reports",
                // GET /api/v1/user-reports/my-reports - Get own reports (any authenticated user)
                "/api/v1/user-reports/my-reports",
                // POST /api/v1/user-reports/{reportId}/driver-response - Driver response (authenticated driver)
                "/api/v1/user-reports/*/driver-response",
                // POST /api/v1/rides/{rideId}/report - Submit ride-specific report (any authenticated user)
                "/api/v1/rides/*/report",
                // Note: GET /api/v1/user-reports, GET /{reportId}, POST /{reportId}/resolve, 
                // PATCH /{reportId} are protected by @PreAuthorize("hasRole('ADMIN')") in controller
                // Ride requests (view details, cancel own requests - specific first)
                "/api/v1/ride-requests/rider/*",
                "/api/v1/ride-requests/*",
                // Rides (view details - specific first)
                "/api/v1/rides/*",
                // Quotes
                "/api/v1/quotes",
                // Routes
                "/api/v1/routes/templates",
                // Refunds (user operations)
                "/api/v1/refunds/requests/my",
                "/api/v1/refunds/requests/*/cancel",
                "/api/v1/refunds/requests/*",
                "/api/v1/refunds/requests",
                // File upload
                "/api/v1/files/upload",
                // Notifications, transactions, locations, etc. (if they exist)
                "/api/v1/notifications/**",
                "/api/v1/transactions/**",
                "/api/v1/locations/**",
                "/api/v1/fcm-tokens/**",
                "/api/v1/booking-wallet/**",
                "/api/v1/dashboard/**",
                "/api/v1/ratings/**",
                "/api/v1/tracking/**",
                "/api/v1/payos/**"
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
                        // Public endpoints (no authentication required)
                        .requestMatchers(SecurityEndpoints.PUBLIC_PATHS).permitAll()

                        // Admin-only endpoints
                        .requestMatchers(SecurityEndpoints.ADMIN_PATHS).hasRole("ADMIN")

                        // Admin or Staff endpoints
                        .requestMatchers(SecurityEndpoints.ADMIN_STAFF_PATHS).hasAnyRole("ADMIN", "STAFF")

                        // Rider-specific endpoints
                        .requestMatchers(SecurityEndpoints.RIDER_PATHS).hasRole("RIDER")

                        // Driver-specific endpoints
                        .requestMatchers(SecurityEndpoints.DRIVER_PATHS).hasRole("DRIVER")

                        // Authenticated endpoints (any authenticated user)
                        .requestMatchers(SecurityEndpoints.AUTHENTICATED_PATHS).authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
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
        
        // Allow multiple origins including Vercel deployment
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",
            "http://localhost:8080",
            "https://*.vercel.app",
            "https://frontend-web-capstone.vercel.app",
            "*" // Allow all for development - remove in production
        ));
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        // Expose headers that frontend might need
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
