package com.mssus.app.appconfig.security;

import com.mssus.app.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtPrincipalExtractor {
    private final UserRepository userRepository;

    @Value("${security.jwt.secret-key}")
    private String jwtSecret;

    public UserDetails extractUserDetails(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token.replace("Bearer ", ""))
                .getBody();

            String email = claims.get("sub", String.class);
            Integer userId = getUserIdByEmail(email);
            List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_RIDER")
            );

            return new User(String.valueOf(userId), "", authorities);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage());
        }
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Integer getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
            .map(com.mssus.app.entity.User::getUserId)
            .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
    }
}
