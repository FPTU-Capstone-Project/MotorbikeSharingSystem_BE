package com.mssus.app.repository;

import com.mssus.app.entity.AdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AdminProfileRepository extends JpaRepository<AdminProfile, Integer> {

    Optional<AdminProfile> findByUserUserId(Integer userId);

    @Modifying
    @Query("UPDATE AdminProfile a SET a.lastLogin = :lastLogin WHERE a.adminId = :adminId")
    void updateLastLogin(@Param("adminId") Integer adminId, @Param("lastLogin") LocalDateTime lastLogin);
}
