package com.mssus.app.repository;

import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmailOrPhone(String email, String phone);

    Optional<User> findByEmailAndStatus(String phone, UserStatus status);

    Optional<User> findByEmailAndStatusNot(String phone, UserStatus status);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailAndStatusNot(String email, UserStatus status);

    boolean existsByEmailAndStatus(String email, UserStatus status);

    boolean existsByStudentId(String studentId);


    @Query("SELECT u FROM User u LEFT JOIN FETCH u.riderProfile LEFT JOIN FETCH u.driverProfile " +
           "LEFT JOIN FETCH u.wallet WHERE u.email = :email")
    Optional<User> findByEmailWithProfiles(@Param("email") String email);

    @EntityGraph(attributePaths = {"riderProfile", "driverProfile"})
    Optional<User> findById(Integer userId);

    @EntityGraph(attributePaths = {"riderProfile", "driverProfile"})
    Page<User> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"riderProfile", "driverProfile"})
    Page<User> findByUserType(UserType userType, Pageable pageable);

    @EntityGraph(attributePaths = {"riderProfile", "driverProfile"})
    Page<User> findByStatus(UserStatus status, Pageable pageable);


    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.driverProfile IS NOT NULL AND u.userId = :userId")
    boolean isDriver(@Param("userId") Integer userId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.riderProfile IS NOT NULL AND u.userId = :userId")
    boolean isRider(@Param("userId") Integer userId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailWithLock(@Param("email") String email);

    List<User> findByUserType(UserType userType);
}
