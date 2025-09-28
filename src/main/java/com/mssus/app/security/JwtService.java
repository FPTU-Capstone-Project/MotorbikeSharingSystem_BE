package com.mssus.app.security;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.entity.User;
import com.mssus.app.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {
    @Autowired
    private UserRepository userRepository;

    @Value("${security.jwt.secret-key}")
    private String jwtSecret;

    @Value("${security.jwt.expiration-time}")
    private Long jwtExpiration;

    @Value("${security.jwt.refresh-expiration}")
    private Long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private int extractTokenVersion(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object version = claims.get("token_version");
            if (version instanceof Integer) {
                return (Integer) version;
            } else if (version instanceof String) {
                return Integer.parseInt((String) version);
            } else if (version == null) {
                log.error("Token version claim is missing");
                throw new IllegalArgumentException("Token version claim is missing");
            } else {
                log.error("Invalid token version type: {}", version.getClass().getSimpleName());
                throw new IllegalArgumentException("Invalid token version type");
            }
        } catch (ClassCastException ex) {
            log.error("Error casting token version: {}", ex.getMessage());
            throw new IllegalArgumentException("Invalid token version format", ex);
        } catch (NumberFormatException ex) {
            log.error("Error parsing token version string: {}", ex.getMessage());
            throw new IllegalArgumentException("Invalid token version number format", ex);
        } catch (Exception ex) {
            log.error("Unexpected error extracting token version: {}", ex.getMessage());
            throw new IllegalArgumentException("Error extracting token version", ex);
        }
    }


    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            final User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> BaseDomainException.formatted("user.not-found.by-email", "User with email not found: %s", username));

            return (username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && user.getTokenVersion() == extractTokenVersion(token));

        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("JWT token is expired: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("JWT token is unsupported: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username, jwtExpiration);
    }

    public String generateToken(String username, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        return createToken(claims, username, jwtExpiration);
    }

    public String generateRefreshToken(String username, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("token_version", tokenVersion);
        return createToken(claims, username, refreshExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            return extractUsername(token);
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }

    public Long getExpirationTime() {
        return jwtExpiration;
    }
}
