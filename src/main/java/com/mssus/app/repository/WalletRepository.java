package com.mssus.app.repository;

import com.mssus.app.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {

    @Query("SELECT w FROM Wallet w WHERE w.user.userId = :userId")
    Optional<Wallet> findByUserId(@Param("userId") Integer userId);

    @Query("SELECT COUNT(w) > 0 FROM Wallet w WHERE w.user.userId = :userId")
    boolean existsByUserId(@Param("userId") Integer userId);
}
