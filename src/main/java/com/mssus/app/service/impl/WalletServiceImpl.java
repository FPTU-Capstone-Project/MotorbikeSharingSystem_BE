package com.mssus.app.service.impl;

import com.mssus.app.dto.response.wallet.WalletResponse;
import com.mssus.app.entity.Wallet;
import com.mssus.app.common.exception.NotFoundException;
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
    public WalletResponse initiateWallet(Integer userId) {
        var user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.userNotFound(userId));
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setPspAccountId("");
//        wallet.setCachedBalance(BigDecimal.ZERO);

        return null;
    }

    @Override
    public WalletResponse getWalletByUserId(Integer userId) {
        return null;
    }

    @Override
    public WalletResponse getWalletById(Integer walletId) {
        return null;
    }

    @Override
    public void activateWallet(Integer walletId) {

    }

    @Override
    public void deactivateWallet(Integer walletId) {

    }

    @Override
    public BigDecimal getAvailableBalance(Integer userId) {
        return null;
    }

    @Override
    public BigDecimal getPendingBalance(Integer userId) {
        return null;
    }

    @Override
    public void syncWalletWithPSP(Integer walletId) {

    }

    @Override
    public boolean isWalletOwner(Integer walletId, String username) {
        return false;
    }

    @Override
    public List<WalletResponse> getActiveWallets() {
        return null;
    }
}
