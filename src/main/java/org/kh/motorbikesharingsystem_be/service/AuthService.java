package org.kh.motorbikesharingsystem_be.service;

import org.kh.motorbikesharingsystem_be.dto.LoginRequest;
import org.kh.motorbikesharingsystem_be.dto.LoginResponse;
import org.kh.motorbikesharingsystem_be.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void register(RegisterRequest request);
}
