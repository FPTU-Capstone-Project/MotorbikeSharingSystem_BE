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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtPrincipalExtractor {
    @Value("${security.jwt.secret-key}")
    private String jwtSecret;

    private UserRepository userRepository;

    public UserDetails extractUserDetails(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token.replace("Bearer ", ""))
                .getBody();

            Integer userId = claims.get("userId", Integer.class);
            if (userId == null) {
                // Fallback: parse from sub formatted as "user-<id>"
                String sub = claims.getSubject();
                if (sub != null && sub.startsWith("user-")) {
                    try {
                        userId = Integer.parseInt(sub.substring("user-".length()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (userId == null) {
                throw new IllegalArgumentException("Missing userId in JWT");
            }

            String activeProfile = claims.get("active_profile", String.class);
            List<String> profiles = claims.get("profiles", List.class);

            Set<GrantedAuthority> authorities = new HashSet<>();
            if (profiles != null) {
                profiles.forEach(p -> authorities.add(new SimpleGrantedAuthority("ROLE_" + p.toUpperCase())));
            }
            if (activeProfile != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + activeProfile.toUpperCase()));
            }

            return new User(String.valueOf(userId), "", new ArrayList<>(authorities));
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
