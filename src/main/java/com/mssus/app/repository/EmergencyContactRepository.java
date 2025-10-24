package com.mssus.app.repository;

import com.mssus.app.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Integer> {

    List<EmergencyContact> findByUser_UserIdOrderByIsPrimaryDescCreatedAtAsc(Integer userId);

    Optional<EmergencyContact> findByUser_UserIdAndIsPrimaryTrue(Integer userId);

    boolean existsByUser_UserId(Integer userId);

    @Modifying
    @Query("""
        update EmergencyContact ec
           set ec.isPrimary = false
         where ec.user.userId = :userId
           and (:contactId is null or ec.contactId <> :contactId)
    """)
    void unsetPrimaryForUser(@Param("userId") Integer userId, @Param("contactId") Integer contactId);
}
