//package com.mssus.app.repository;
//
//import com.mssus.app.entity.UserEntity;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@DataJpaTest
//@ActiveProfiles("test")
//class UserRepositoryTest {
//
//    @Autowired
//    private TestEntityManager entityManager;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    private UserEntity testUser;
//
//    @BeforeEach
//    void setUp() {
//        testUser = UserEntity.builder()
//                .email("test@example.com")
//                .phone("0901234567")
//                .fullName("Test User")
//                .passwordHash("hashedPassword")
//                .userType("student")
//                .isActive(true)
//                .emailVerified(false)
//                .phoneVerified(false)
//                .build();
//    }
//
//    @Test
//    void findByEmail_UserExists_ReturnsUser() {
//        // Arrange
//        entityManager.persistAndFlush(testUser);
//
//        // Act
//        Optional<UserEntity> result = userRepository.findByEmail("test@example.com");
//
//        // Assert
//        assertTrue(result.isPresent());
//        assertEquals("test@example.com", result.get().getEmail());
//        assertEquals("Test User", result.get().getFullName());
//    }
//
//    @Test
//    void findByEmail_UserNotExists_ReturnsEmpty() {
//        // Act
//        Optional<UserEntity> result = userRepository.findByEmail("nonexistent@example.com");
//
//        // Assert
//        assertFalse(result.isPresent());
//    }
//
//    @Test
//    void findByPhone_UserExists_ReturnsUser() {
//        // Arrange
//        entityManager.persistAndFlush(testUser);
//
//        // Act
//        Optional<UserEntity> result = userRepository.findByPhone("0901234567");
//
//        // Assert
//        assertTrue(result.isPresent());
//        assertEquals("0901234567", result.get().getPhone());
//    }
//
//    @Test
//    void findByEmailOrPhone_WithEmail_ReturnsUser() {
//        // Arrange
//        entityManager.persistAndFlush(testUser);
//
//        // Act
//        Optional<UserEntity> result = userRepository.findByEmailOrPhone("test@example.com", "test@example.com");
//
//        // Assert
//        assertTrue(result.isPresent());
//        assertEquals("test@example.com", result.get().getEmail());
//    }
//
//    @Test
//    void findByEmailOrPhone_WithPhone_ReturnsUser() {
//        // Arrange
//        entityManager.persistAndFlush(testUser);
//
//        // Act
//        Optional<UserEntity> result = userRepository.findByEmailOrPhone("0901234567", "0901234567");
//
//        // Assert
//        assertTrue(result.isPresent());
//        assertEquals("0901234567", result.get().getPhone());
//    }
//
//    @Test
//    void existsByEmail_UserExists_ReturnsTrue() {
//        // Arrange
//        entityManager.persistAndFlush(testUser);
//
//        // Act
//        boolean exists = userRepository.existsByEmail("test@example.com");
//
//        // Assert
//        assertTrue(exists);
//    }
//
//    @Test
//    void existsByEmail_UserNotExists_ReturnsFalse() {
//        // Act
//        boolean exists = userRepository.existsByEmail("nonexistent@example.com");
//
//        // Assert
//        assertFalse(exists);
//    }
//
//    @Test
//    void existsByPhone_UserExists_ReturnsTrue() {
//        // Arrange
//        entityManager.persistAndFlush(testUser);
//
//        // Act
//        boolean exists = userRepository.existsByPhone("0901234567");
//
//        // Assert
//        assertTrue(exists);
//    }
//
//    @Test
//    void existsByPhone_UserNotExists_ReturnsFalse() {
//        // Act
//        boolean exists = userRepository.existsByPhone("0999999999");
//
//        // Assert
//        assertFalse(exists);
//    }
//
//    @Test
//    void save_NewUser_PersistsSuccessfully() {
//        // Act
//        UserEntity savedUser = userRepository.save(testUser);
//
//        // Assert
//        assertNotNull(savedUser.getUserId());
//        assertNotNull(savedUser.getCreatedAt());
//        assertNotNull(savedUser.getUpdatedAt());
//
//        // Verify it can be retrieved
//        Optional<UserEntity> retrieved = userRepository.findById(savedUser.getUserId());
//        assertTrue(retrieved.isPresent());
//        assertEquals("test@example.com", retrieved.get().getEmail());
//    }
//}
