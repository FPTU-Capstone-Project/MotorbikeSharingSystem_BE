package com.mssus.app.repository;

import com.mssus.app.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByEmailOrPhone(String email, String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByStudentId(String studentId);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.riderProfile LEFT JOIN FETCH u.driverProfile " +
           "LEFT JOIN FETCH u.adminProfile LEFT JOIN FETCH u.wallet WHERE u.userId = :userId")
    Optional<UserEntity> findByIdWithProfiles(@Param("userId") Integer userId);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.riderProfile LEFT JOIN FETCH u.driverProfile " +
           "LEFT JOIN FETCH u.adminProfile LEFT JOIN FETCH u.wallet WHERE u.email = :email")
    Optional<UserEntity> findByEmailWithProfiles(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.riderProfile LEFT JOIN FETCH u.driverProfile " +
           "LEFT JOIN FETCH u.adminProfile LEFT JOIN FETCH u.wallet WHERE u.phone = :phone")
    Optional<UserEntity> findByPhoneWithProfiles(@Param("phone") String phone);

    Page<UserEntity> findByUserType(String userType, Pageable pageable);

    Page<UserEntity> findByIsActive(Boolean isActive, Pageable pageable);

    Page<UserEntity> findByUserTypeAndIsActive(String userType, Boolean isActive, Pageable pageable);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u WHERE u.adminProfile IS NOT NULL AND u.userId = :userId")
    boolean isAdmin(@Param("userId") Integer userId);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u WHERE u.driverProfile IS NOT NULL AND u.userId = :userId")
    boolean isDriver(@Param("userId") Integer userId);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u WHERE u.riderProfile IS NOT NULL AND u.userId = :userId")
    boolean isRider(@Param("userId") Integer userId);
}
