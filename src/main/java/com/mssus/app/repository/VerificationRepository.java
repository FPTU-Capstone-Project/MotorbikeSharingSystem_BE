package com.mssus.app.repository;

import com.mssus.app.entity.VerificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationRepository extends JpaRepository<VerificationEntity, Integer> {

    @Query("SELECT v FROM VerificationEntity v WHERE v.user.userId = :userId")
    List<VerificationEntity> findByUserId(@Param("userId") Integer userId);

    @Query("SELECT v FROM VerificationEntity v WHERE v.user.userId = :userId AND v.type = :type")
    List<VerificationEntity> findByUserIdAndType(@Param("userId") Integer userId, @Param("type") String type);

    @Query("SELECT v FROM VerificationEntity v WHERE v.user.userId = :userId AND v.type = :type AND v.status = :status")
    Optional<VerificationEntity> findByUserIdAndTypeAndStatus(@Param("userId") Integer userId, 
                                                              @Param("type") String type, 
                                                              @Param("status") String status);

    Page<VerificationEntity> findByStatus(String status, Pageable pageable);

    Page<VerificationEntity> findByTypeAndStatus(String type, String status, Pageable pageable);

    @Query("SELECT COUNT(v) > 0 FROM VerificationEntity v WHERE v.user.userId = :userId " +
           "AND v.type = :type AND v.status = 'approved'")
    boolean isUserVerifiedForType(@Param("userId") Integer userId, @Param("type") String type);
}
