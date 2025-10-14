package com.mssus.app.repository;

import com.mssus.app.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Integer> {
    List<FcmToken> findByUserUserIdAndIsActive(Integer userId, Boolean isActive);

    Optional<FcmToken> findByToken(String token);
}
