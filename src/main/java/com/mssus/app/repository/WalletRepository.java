package com.mssus.app.repository;

import com.mssus.app.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {

    @Query("SELECT COUNT(w) > 0 FROM Wallet w WHERE w.user.userId = :userId")
    boolean existsByUserId(@Param("userId") Integer userId);


}
