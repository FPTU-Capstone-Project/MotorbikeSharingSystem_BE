package com.mssus.app.security;

import com.mssus.app.entity.Users;
import com.mssus.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Username can be either email or phone
        Users user = userRepository.findByEmailOrPhone(username, username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email or phone: " + username));

        if (!user.getIsActive()) {
            throw new UsernameNotFoundException("User account is disabled");
        }

        return createUserDetails(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Integer userId) {
        Users user = userRepository.findByIdWithProfiles(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        if (!user.getIsActive()) {
            throw new UsernameNotFoundException("User account is disabled");
        }

        return createUserDetails(user);
    }

    private UserDetails createUserDetails(Users user) {
        List<GrantedAuthority> authorities = getAuthorities(user);
        
        return User.builder()
                .username(user.getEmail()) // Use email as username for Spring Security
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(!user.getIsActive())
                .credentialsExpired(false)
                .disabled(!user.getIsActive())
                .build();
    }

    private List<GrantedAuthority> getAuthorities(Users user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Base role for all authenticated users
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        
        // Add role based on profile
        if (user.getAdminProfile() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        
        if (user.getDriverProfile() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_DRIVER"));
            if ("active".equals(user.getDriverProfile().getStatus())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_DRIVER_ACTIVE"));
            }
        }
        
        if (user.getRiderProfile() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_RIDER"));
        }
        
        return authorities;
    }
}
