# Development Guide

This guide provides comprehensive information for developers working on the MSSUS Account module.

## Overview

**For New Team Members**: This guide will help you understand how to set up your development environment, follow our coding standards, and contribute effectively to the project.

**For Experienced Developers**: This serves as a reference for our established patterns, testing strategies, and deployment procedures.

---

## Quick Start for Developers

### Prerequisites

- **Java 17+** (OpenJDK recommended)
- **Maven 3.8+** for dependency management
- **PostgreSQL 15** (or use Docker setup)
- **Docker Desktop** (for containerized development)
- **Git** for version control
- **IDE**: IntelliJ IDEA or VS Code with Java extensions

### Local Development Setup

#### Option 1: Docker Development (Recommended)

```bash
# Clone the repository
git clone <repository-url>
cd backend

# Start development environment
chmod +x dev.sh
./dev.sh

# Application will be available at:
# - API: http://localhost:8081
# - Swagger UI: http://localhost:8081/swagger-ui.html
# - Database: localhost:5432
```

#### Option 2: Native Development

```bash
# Start PostgreSQL database
createdb mssus_db

# Set environment variables
export DB_URL=jdbc:postgresql://localhost:5432/mssus_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=your-256-bit-secret

# Run the application
mvn spring-boot:run
```

---

## Code Structure and Standards

### Project Structure

```
src/main/java/com/mssus/account/
├── config/             # Configuration classes
│   ├── JacksonConfig.java
│   ├── OpenApiConfig.java
│   └── SecurityConfig.java
├── controller/         # REST endpoints
│   └── AuthController.java
├── dto/               # Data Transfer Objects
│   ├── request/       # Request DTOs
│   └── response/      # Response DTOs
├── entity/            # JPA entities
│   ├── UserEntity.java
│   ├── RiderProfileEntity.java
│   └── ...
├── exception/         # Custom exceptions
│   ├── DomainException.java
│   ├── GlobalExceptionHandler.java
│   └── ...
├── mapper/            # MapStruct mappers
│   ├── UserMapper.java
│   └── ...
├── repository/        # Data access layer
│   ├── UserRepository.java
│   └── ...
├── security/          # Security components
│   ├── JwtService.java
│   ├── JwtAuthenticationFilter.java
│   └── ...
├── service/           # Business logic
│   ├── AuthService.java
│   └── impl/
│       └── AuthServiceImpl.java
└── util/              # Utility classes
    ├── Constants.java
    ├── OtpUtil.java
    └── ValidationUtil.java
```

### Coding Standards

#### Naming Conventions

```java
// Classes: PascalCase
public class UserService { }

// Methods and variables: camelCase
public String getUserEmail() { }
private int maxAttempts;

// Constants: UPPER_SNAKE_CASE
public static final String DEFAULT_PROFILE_TYPE = "rider";

// Database tables: snake_case
@Table(name = "user_profiles")

// REST endpoints: kebab-case
@GetMapping("/auth/forgot-password")
```

#### Documentation Standards

```java
/**
 * Registers a new user in the system with automatic profile creation.
 * 
 * @param request Registration request containing user details
 * @return RegisterResponse with user data and JWT token
 * @throws ConflictException if email or phone already exists
 * @throws ValidationException if input data is invalid
 */
@Override
@Transactional
public RegisterResponse register(RegisterRequest request) {
    // Implementation...
}
```

#### Error Handling Patterns

```java
// Use specific custom exceptions
throw new NotFoundException("User not found with ID: " + userId);
throw new ConflictException("Email already exists: " + email);
throw new ValidationException("Password must contain at least 8 characters");

// Don't use generic RuntimeException
// throw new RuntimeException("Something went wrong"); ❌
```

---

## Development Workflow

### Branch Strategy

```
main
├── develop                 # Development branch
├── feature/user-login     # Feature branches
├── feature/profile-update
├── hotfix/security-patch  # Hotfix branches
└── release/v1.0          # Release branches
```

### Commit Message Format

```
feat(auth): add password strength validation

- Implement password complexity requirements
- Add unit tests for validation logic
- Update API documentation

Closes #123
```

**Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

### Pull Request Process

1. **Create Feature Branch**:
   ```bash
   git checkout -b feature/new-feature
   ```

2. **Make Changes**: Follow coding standards and add tests

3. **Run Quality Checks**:
   ```bash
   # Run tests
   mvn test
   
   # Check code coverage
   mvn jacoco:report
   
   # Static analysis
   mvn spotbugs:check
   ```

4. **Create Pull Request**: Include description, testing notes, and screenshots

5. **Code Review**: Address feedback and ensure CI passes

6. **Merge**: Use squash merge for clean history

---

## Testing Strategy

### Test Structure

```
src/test/java/com/mssus/account/
├── controller/            # Integration tests
│   └── AuthControllerTest.java
├── repository/            # Data access tests
│   └── UserRepositoryTest.java
├── security/              # Security component tests
│   └── JwtServiceTest.java
├── service/               # Business logic tests
│   └── AuthServiceTest.java
└── util/                  # Utility tests
    └── ValidationUtilTest.java
```

### Testing Best Practices

#### Unit Tests Example

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private AuthServiceImpl authService;
    
    @Test
    @DisplayName("Should register user successfully with valid data")
    void register_WithValidData_ShouldReturnSuccess() {
        // Given
        RegisterRequest request = createValidRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        
        // When
        RegisterResponse result = authService.register(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(request.getEmail());
        verify(userRepository).save(any(UserEntity.class));
    }
    
    @Test
    @DisplayName("Should throw ConflictException when email already exists")
    void register_WithExistingEmail_ShouldThrowConflictException() {
        // Given
        RegisterRequest request = createValidRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        // When/Then
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Email already exists: " + request.getEmail());
    }
}
```

#### Integration Tests Example

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AuthControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void register_WithValidData_ShouldReturn201() throws Exception {
        String requestBody = """
            {
                "fullName": "Test User",
                "email": "test@university.edu.vn",
                "phone": "0901234567",
                "password": "Password123",
                "confirmPassword": "Password123"
            }
            """;
        
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("test@university.edu.vn"))
                .andExpect(jsonPath("$.token").exists());
    }
}
```

### Test Coverage Goals

- **Unit Tests**: 80%+ coverage
- **Integration Tests**: All controller endpoints
- **E2E Tests**: Critical user flows
- **Performance Tests**: Response time validation

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AuthServiceTest

# Run tests with coverage
mvn test jacoco:report

# Run integration tests only
mvn test -Dtest="**/*IntegrationTest"
```

---

## Database Development

### Migration Best Practices

#### Creating Migrations

```sql
-- V6__add_user_preferences.sql
-- Add user preference settings table

CREATE TABLE IF NOT EXISTS user_preferences (
    preference_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    notification_email BOOLEAN DEFAULT true,
    notification_sms BOOLEAN DEFAULT false,
    language VARCHAR(10) DEFAULT 'vi',
    timezone VARCHAR(50) DEFAULT 'Asia/Ho_Chi_Minh',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_preferences_user 
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Add indexes for performance
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);

-- Add default preferences for existing users
INSERT INTO user_preferences (user_id) 
SELECT user_id FROM users WHERE user_id NOT IN (
    SELECT user_id FROM user_preferences
);
```

#### Migration Guidelines

1. **Never modify existing migrations** - create new ones
2. **Use descriptive names** - `V6__add_user_preferences.sql`
3. **Include rollback instructions** in comments
4. **Test migrations** on copy of production data
5. **Keep migrations small** and focused

### Database Testing

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void findByEmail_ExistingUser_ShouldReturnUser() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("test@example.com")
                .fullName("Test User")
                .build();
        entityManager.persistAndFlush(user);
        
        // When
        Optional<UserEntity> found = userRepository.findByEmail("test@example.com");
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Test User");
    }
}
```

---

## Security Development

### JWT Security Best Practices

```java
// Secure JWT configuration
@Value("${jwt.secret}")
private String jwtSecret; // Use 256-bit key

@Value("${jwt.expiration:3600000}") // 1 hour default
private Long jwtExpiration;

// Token validation
public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
}
```

### Input Validation

```java
// Use Bean Validation annotations
@Data
@Builder
public class RegisterRequest {
    
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    private String fullName;
    
    @Email(message = "Email must be valid")
    @Pattern(regexp = ".*@.*\\.(edu\\.vn|edu)$", message = "Must use educational email")
    private String email;
    
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must contain uppercase, lowercase, number, special character, min 8 chars"
    )
    private String password;
}
```

### Security Testing

```java
@Test
void login_WithInvalidCredentials_ShouldReturn401() throws Exception {
    String requestBody = """
        {
            "email": "test@example.com",
            "password": "wrongpassword"
        }
        """;
    
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
            .andExpected(jsonPath("$.message").value("Invalid credentials"));
}

@Test
void getProfile_WithoutToken_ShouldReturn401() throws Exception {
    mockMvc.perform(get("/api/v1/profile"))
            .andExpect(status().isUnauthorized());
}

@Test
void getProfile_WithExpiredToken_ShouldReturn401() throws Exception {
    String expiredToken = jwtService.generateExpiredToken("test@example.com");
    
    mockMvc.perform(get("/api/v1/profile")
            .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
}
```

---

## Performance Optimization

### Database Query Optimization

```java
// Good: Use JOIN FETCH to prevent N+1 queries
@Query("SELECT u FROM UserEntity u " +
       "LEFT JOIN FETCH u.riderProfile " +
       "LEFT JOIN FETCH u.driverProfile " +
       "WHERE u.userId = :userId")
Optional<UserEntity> findByIdWithProfiles(@Param("userId") Integer userId);

// Good: Use pagination for large results
@Query("SELECT u FROM UserEntity u WHERE u.isActive = true")
Page<UserEntity> findActiveUsers(Pageable pageable);

// Bad: Don't load unnecessary data
// List<UserEntity> findAll(); // Loads everything
```

### Caching Strategy

```java
@Service
public class UserService {
    
    @Cacheable(value = "userProfiles", key = "#userId", unless = "#result == null")
    public UserProfileResponse getUserProfile(Integer userId) {
        // Cache user profiles for 5 minutes
        return userRepository.findByIdWithProfiles(userId)
                .map(userMapper::toProfileResponse)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
    
    @CacheEvict(value = "userProfiles", key = "#userId")
    public void updateUserProfile(Integer userId, UpdateProfileRequest request) {
        // Evict cache when profile is updated
    }
}
```

### Performance Testing

```java
@Test
@Timeout(value = 2, unit = TimeUnit.SECONDS)
void register_ShouldCompleteWithin2Seconds() {
    RegisterRequest request = createValidRegisterRequest();
    
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    
    RegisterResponse result = authService.register(request);
    
    stopWatch.stop();
    assertThat(result).isNotNull();
    assertThat(stopWatch.getTotalTimeMillis()).isLessThan(2000);
}
```

---

## Debugging and Troubleshooting

### Logging Configuration

```java
// Use SLF4J with structured logging
@Slf4j
@Service
public class AuthServiceImpl {
    
    public RegisterResponse register(RegisterRequest request) {
        log.info("User registration attempt for email: {}", request.getEmail());
        
        try {
            // Business logic
            log.info("User registered successfully with ID: {}", user.getUserId());
            return response;
        } catch (ConflictException e) {
            log.warn("Registration failed - email already exists: {}", request.getEmail());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during registration for email: {}", 
                     request.getEmail(), e);
            throw e;
        }
    }
}
```

### Common Issues and Solutions

#### Database Connection Issues

```bash
# Check if PostgreSQL is running
docker ps | grep postgres

# Check connection from application
docker logs motorbike-dev-app | grep -i "connection"

# Reset database
./dev.sh stop
./dev.sh  # Will recreate containers
```

#### JWT Token Issues

```java
// Debug token validation
@Test
void debugTokenValidation() {
    String token = jwtService.generateToken("test@example.com");
    
    log.debug("Generated token: {}", token);
    log.debug("Token valid: {}", jwtService.isTokenValid(token, userDetails));
    log.debug("Token expired: {}", jwtService.isTokenExpired(token));
    log.debug("Username from token: {}", jwtService.extractUsername(token));
}
```

#### Memory and Performance Issues

```bash
# Monitor application performance
docker stats motorbike-dev-app

# Check for memory leaks
jmap -dump:format=b,file=heapdump.hprof <pid>

# Profile SQL queries
# Add to application-dev.yml:
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

---

## Deployment and DevOps

### Environment Configuration

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mssus_dev
    username: postgres
    password: postgres
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

jwt:
  secret: dev-secret-key-change-in-production
  expiration: 3600000

logging:
  level:
    com.mssus.app: DEBUG
    org.springframework.security: DEBUG
```

```yaml
# application-prod.yml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600000}

logging:
  level:
    com.mssus.app: INFO
    root: WARN
```

### Health Checks

```java
@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public Health health() {
        try {
            long userCount = userRepository.count();
            return Health.up()
                    .withDetail("users", userCount)
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "Connected")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

### Monitoring and Metrics

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## Contributing Guidelines

### Code Review Checklist

**Functionality**:
- [ ] Code accomplishes the intended purpose
- [ ] Edge cases are handled properly
- [ ] Error handling is comprehensive
- [ ] Performance impact is considered

**Code Quality**:
- [ ] Follows established coding standards
- [ ] Methods are focused and single-purpose
- [ ] Variable names are descriptive
- [ ] Code is self-documenting

**Testing**:
- [ ] Unit tests cover new functionality
- [ ] Integration tests verify API behavior
- [ ] Test names clearly describe scenarios
- [ ] Edge cases and error paths are tested

**Security**:
- [ ] Input validation is proper
- [ ] SQL injection prevention
- [ ] XSS prevention measures
- [ ] Authentication/authorization is correct

**Documentation**:
- [ ] API documentation is updated
- [ ] Code comments explain complex logic
- [ ] README changes if needed
- [ ] Migration scripts are documented

### Getting Help

1. **Documentation**: Check this guide and other docs first
2. **Code Examples**: Look at existing implementations
3. **Team Chat**: Ask in development channel
4. **Code Review**: Request review early and often
5. **Pair Programming**: Schedule sessions for complex features

### Resources

- **Spring Boot Documentation**: https://spring.io/projects/spring-boot
- **Spring Security Reference**: https://docs.spring.io/spring-security/
- **JPA/Hibernate Guide**: https://hibernate.org/orm/documentation/
- **Testing Guide**: https://spring.io/guides/gs/testing-web/
- **Docker Documentation**: https://docs.docker.com/

---

This development guide is a living document. Please contribute improvements and keep it updated as the project evolves.
