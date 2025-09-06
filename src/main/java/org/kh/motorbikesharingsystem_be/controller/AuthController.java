package org.kh.motorbikesharingsystem_be.controller;

import org.kh.motorbikesharingsystem_be.dto.LoginRequest;
import org.kh.motorbikesharingsystem_be.dto.LoginResponse;
import org.kh.motorbikesharingsystem_be.dto.RegisterRequest;
import org.kh.motorbikesharingsystem_be.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request)  {
        LoginResponse loginResponse = authService.login(request);
        return ResponseEntity.ok(loginResponse);
    }
    @PostMapping("/register")
    ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok().build();
    }
}
