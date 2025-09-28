package com.mssus.app.controller;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.common.exception.catalog.ErrorCatalogService;
import com.mssus.app.common.exception.catalog.ErrorEntry;
import com.mssus.app.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestController {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private ErrorCatalogService errorCatalogService;

    @GetMapping("/test/password")
    public String testPassword() {
        String rawPassword = "Admin123!";
        String correctHash = passwordEncoder.encode(rawPassword);

        try {
            String hashFromDb = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'admin@mssus.com'",
                String.class
            );

            boolean matches = passwordEncoder.matches(rawPassword, hashFromDb);

            return "Email found: " + (hashFromDb != null) +
                "\nPassword matches: " + matches +
                "\nCurrent hash from DB: " + hashFromDb +
                "\nCorrect hash for 'Admin123!': " + correctHash +
                "\n\nSQL to fix: UPDATE users SET password_hash = '" + correctHash + "' WHERE email = 'admin@mssus.com';";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/test/debug-login")
    public String debugLogin() {
        String testEmail = "admin@mssus.com";
        String testPassword = "Admin123!";

        try {
            // Get user data from database
            Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT email, password_hash, user_type, is_active FROM users WHERE email = ?",
                testEmail
            );

            String dbHash = (String) user.get("password_hash");
            Boolean isActive = (Boolean) user.get("is_active");

            // Test multiple common passwords
            String[] commonPasswords = {"Admin123!", "admin123", "password", "admin", "123456"};
            StringBuilder results = new StringBuilder();

            results.append("User found: ").append(user).append("\n");
            results.append("Is active: ").append(isActive).append("\n\n");

            for (String pwd : commonPasswords) {
                boolean matches = passwordEncoder.matches(pwd, dbHash);
                results.append("Password '").append(pwd).append("': ").append(matches).append("\n");
            }

            // Generate fresh hash for Admin123!
            String freshHash = passwordEncoder.encode("Admin123!");
            results.append("\nFresh hash for 'Admin123!': ").append(freshHash);

            return results.toString();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/test/auth-flow")
    public String testAuthFlow() {
        String testEmail = "admin@mssus.com";
        String testPassword = "Admin123!";

        try {
            // Test 1: Direct database check
            Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT * FROM users WHERE email = ?", testEmail
            );

            String dbHash = (String) user.get("password_hash");
            boolean directMatch = passwordEncoder.matches(testPassword, dbHash);

            // Test 2: UserDetailsService check
            UserDetails userDetails = null;
            try {
                userDetails = userDetailsService.loadUserByUsername(testEmail);
            } catch (Exception e) {
                return "UserDetailsService failed: " + e.getMessage();
            }

            boolean userDetailsMatch = passwordEncoder.matches(testPassword, userDetails.getPassword());

            // Test 3: Authentication Manager check
            boolean authManagerWorks = false;
            try {
                Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(testEmail, testPassword)
                );
                authManagerWorks = auth.isAuthenticated();
            } catch (Exception e) {
                return "AuthenticationManager failed: " + e.getMessage() +
                    "\nDirect DB match: " + directMatch +
                    "\nUserDetails match: " + userDetailsMatch +
                    "\nDB Hash: " + dbHash +
                    "\nUserDetails Hash: " + userDetails.getPassword();
            }

            return "All tests passed: " + authManagerWorks +
                "\nDirect DB match: " + directMatch +
                "\nUserDetails match: " + userDetailsMatch +
                "\nUser active: " + userDetails.isEnabled() +
                "\nUser authorities: " + userDetails.getAuthorities();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/test/verify-fix")
    public String verifyFix() {
        String testEmail = "admin@mssus.com";
        String testPassword = "Admin123!";

        try {
            // Check if password now matches
            String hashFromDb = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?",
                String.class, testEmail
            );

            boolean matches = passwordEncoder.matches(testPassword, hashFromDb);

            if (matches) {
                // Test full authentication
                try {
                    Authentication auth = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(testEmail, testPassword)
                    );
                    return "SUCCESS: Authentication works! User: " + auth.getName() +
                        ", Authorities: " + auth.getAuthorities();
                } catch (Exception e) {
                    return "Password matches but authentication failed: " + e.getMessage();
                }
            } else {
                return "FAILED: Password still doesn't match. Hash: " + hashFromDb;
            }

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/debug/catalog-test")
    public ResponseEntity<?> testCatalog() {
        try {
            ErrorEntry entry = errorCatalogService.getErrorEntry("user.not-found.by-id");
            return ResponseEntity.ok(Map.of(
                "working", true,
                "errorId", entry.getId(),
                "message", entry.getMessageTemplate(),
                "domain", entry.getDomain(),
                "httpStatus", entry.getHttpStatus(),
                "totalCatalogEntries", errorCatalogService.getAllErrorEntries().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "working", false,
                "error", e.getMessage(),
                "stackTrace", e.getStackTrace()[0].toString()
            ));
        }
    }

    @GetMapping("/debug/throw-test")
    public ResponseEntity<?> testThrowException() {
        // This should trigger your GlobalExceptionHandler
        throw BaseDomainException.of("user.not-found.by-id", "Test exception");
    }
}
