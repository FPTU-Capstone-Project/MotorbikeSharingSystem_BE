package com.mssus.app.controller;

import com.mssus.app.dto.request.FcmTokenRequest;
import com.mssus.app.service.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmService fcmService;

    @PostMapping("/register")
    public ResponseEntity<Void> registerToken(@RequestBody FcmTokenRequest request,
                                              Authentication authentication) {
        fcmService.registerToken(authentication, request.token(), request.deviceType());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/deactivate")
    public ResponseEntity<Void> deactivateToken(@RequestParam String token) {
        fcmService.deactivateToken(token);
        return ResponseEntity.ok().build();
    }
}
