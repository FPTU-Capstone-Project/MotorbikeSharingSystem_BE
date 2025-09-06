package org.kh.motorbikesharingsystem_be.service.impl;

import org.kh.motorbikesharingsystem_be.dto.LoginRequest;
import org.kh.motorbikesharingsystem_be.dto.LoginResponse;
import org.kh.motorbikesharingsystem_be.dto.RegisterRequest;
import org.kh.motorbikesharingsystem_be.model.Users;
import org.kh.motorbikesharingsystem_be.repository.UserRepos;
import org.kh.motorbikesharingsystem_be.security.JwtService;
import org.kh.motorbikesharingsystem_be.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepos userRepository;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService, PasswordEncoder passwordEncoder, UserRepos userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        try{
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtService.generateToken(userDetails);

            return new LoginResponse(
                    token,
                    userDetails.getUsername(),
                    userDetails.getAuthorities().stream()
                            .findFirst()
                            .map(GrantedAuthority::getAuthority)
                            .orElse("USER")
            );
        }catch (Exception e){
            throw new RuntimeException("Login Failed", e);
        }
    }

    @Override
    public void register(RegisterRequest request) {
        Users user = new Users();
        user.setEmail(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Users.Role.USER);
        user.setCreatedAt(LocalDate.now());
        user.setUpdatedAt(LocalDate.now());
        userRepository.save(user);
    }
}
