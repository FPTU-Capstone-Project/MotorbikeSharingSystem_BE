package com.mssus.app.service.impl;

import com.mssus.app.dto.response.wallet.WalletResponse;
import com.mssus.app.entity.Users;
import com.mssus.app.entity.Wallet;
import com.mssus.app.exception.NotFoundException;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    @Override
    public void updateWalletBalanceOnTopUp(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));

        wallet.setShadowBalance(wallet.getShadowBalance().add(amount));
        wallet.setTotalToppedUp(wallet.getTotalToppedUp().add(amount));
        walletRepository.save(wallet);
    }

    @Override
    public void increasePendingBalance(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));

        wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
        walletRepository.save(wallet);
    }

    @Override
    public void decreasePendingBalance(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));

        wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
        walletRepository.save(wallet);
    }

    @Override
    public void transferPendingToAvailable(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
        BigDecimal shadowBalance = BigDecimal.ZERO;
        if (wallet.getShadowBalance() != null){
            shadowBalance = wallet.getShadowBalance();
        }
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
        wallet.setShadowBalance(shadowBalance.add(amount));
        wallet.setTotalToppedUp(wallet.getTotalToppedUp().add(amount));
        walletRepository.save(wallet);
    }
}
