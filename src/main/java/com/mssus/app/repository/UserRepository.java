package com.mssus.app.repository;

import com.mssus.app.entity.Users;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, Integer> {

    Optional<Users> findByEmail(String email);

    Optional<Users> findByPhone(String phone);

    Optional<Users> findByEmailOrPhone(String email, String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByStudentId(String studentId);

    @Query("SELECT u FROM Users u LEFT JOIN FETCH u.riderProfile LEFT JOIN FETCH u.driverProfile " +
           "LEFT JOIN FETCH u.adminProfile LEFT JOIN FETCH u.wallet WHERE u.userId = :userId")
    Optional<Users> findByIdWithProfiles(@Param("userId") Integer userId);

    @Query("SELECT u FROM Users u LEFT JOIN FETCH u.riderProfile LEFT JOIN FETCH u.driverProfile " +
           "LEFT JOIN FETCH u.adminProfile LEFT JOIN FETCH u.wallet WHERE u.email = :email")
    Optional<Users> findByEmailWithProfiles(@Param("email") String email);

    @Query("SELECT u FROM Users u LEFT JOIN FETCH u.riderProfile LEFT JOIN FETCH u.driverProfile " +
           "LEFT JOIN FETCH u.adminProfile LEFT JOIN FETCH u.wallet WHERE u.phone = :phone")
    Optional<Users> findByPhoneWithProfiles(@Param("phone") String phone);

    Page<Users> findByUserType(String userType, Pageable pageable);

    Page<Users> findByIsActive(Boolean isActive, Pageable pageable);

    Page<Users> findByUserTypeAndIsActive(String userType, Boolean isActive, Pageable pageable);

    @Query("SELECT COUNT(u) > 0 FROM Users u WHERE u.adminProfile IS NOT NULL AND u.userId = :userId")
    boolean isAdmin(@Param("userId") Integer userId);

    @Query("SELECT COUNT(u) > 0 FROM Users u WHERE u.driverProfile IS NOT NULL AND u.userId = :userId")
    boolean isDriver(@Param("userId") Integer userId);

    @Query("SELECT COUNT(u) > 0 FROM Users u WHERE u.riderProfile IS NOT NULL AND u.userId = :userId")
    boolean isRider(@Param("userId") Integer userId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT u FROM Users u WHERE u.email = :email")
    Optional<Users> findByEmailWithLock(@Param("email") String email);
}
