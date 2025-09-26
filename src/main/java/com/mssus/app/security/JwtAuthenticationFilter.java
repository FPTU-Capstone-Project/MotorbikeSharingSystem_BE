package com.mssus.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.exception.catalog.ErrorCatalogService;
import com.mssus.app.common.exception.catalog.ErrorEntry;
import com.mssus.app.dto.response.ErrorDetail;
import com.mssus.app.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    private final ErrorCatalogService errorCatalogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        String path = request.getRequestURI();

        if (isInvalidPath(path)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorEntry errorEntry = errorCatalogService.getErrorEntry("system.not-found.resource");

            ErrorDetail errorDetail = ErrorDetail.builder()
                .id("system.not-found.resource")
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
                .path(path)
                .build();

            objectMapper.writeValue(response.getOutputStream(), errorResponse);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        
        try {
            username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                
                if (jwtService.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    log.debug("JWT authentication successful for user: {}", username);
                } else {
                    log.warn("JWT validation failed for token");
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isInvalidPath(String path) {
        // First check for obviously malicious paths
        if (path.contains("..") ||
            path.endsWith(".php") ||
            path.endsWith(".asp") ||
            path.contains("wp-admin") ||
            path.contains("phpmyadmin")) {
            return true;
        }

        // Check if the path matches any legitimate API patterns
        return !isLegitimateApiPath(path);
    }

    private boolean isLegitimateApiPath(String path) {
        // Check against all defined endpoint patterns
        return matchesAnyPattern(path, SecurityConfig.SecurityEndpoints.PUBLIC_PATHS) ||
            matchesAnyPattern(path, SecurityConfig.SecurityEndpoints.PRIVATE_PATHS) ||
            matchesAnyPattern(path, SecurityConfig.SecurityEndpoints.ADMIN_PATHS) ||
            matchesAnyPattern(path, SecurityConfig.SecurityEndpoints.USER_PATHS) ||
            matchesAnyPattern(path, SecurityConfig.SecurityEndpoints.RIDER_PATHS) ||
            matchesAnyPattern(path, SecurityConfig.SecurityEndpoints.DRIVER_PATHS);
    }

    private boolean matchesAnyPattern(String path, String[] patterns) {
        for (String pattern : patterns) {
            if (matchesPattern(path, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String path, String pattern) {
        // Handle ** wildcard (matches any number of path segments)
        if (pattern.endsWith("/**")) {
            String basePattern = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(basePattern);
        }

        // Handle * wildcard (matches single path segment)
        if (pattern.contains("*")) {
            return path.matches(pattern.replace("*", "[^/]*"));
        }

        // Handle path variables like {id}
        if (pattern.contains("{") && pattern.contains("}")) {
            String regex = pattern.replaceAll("\\{[^}]+\\}", "[^/]+");
            return path.matches(regex);
        }

        // Exact match
        return path.equals(pattern);
    }
}
