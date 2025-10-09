package com.mssus.app.security;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.entity.User;
import com.mssus.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
        User user = userRepository.findByEmailOrPhone(username, username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email or phone: " + username));

        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            throw new UsernameNotFoundException("User account is suspended");
        }

        return createUserDetails(user);
    }


    private UserDetails createUserDetails(User user) {
        List<GrantedAuthority> authorities = getAuthorities(user);
        
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail()) // Use email as username for Spring Security
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(user.getStatus().equals(UserStatus.SUSPENDED))
                .credentialsExpired(false)
                .disabled(user.getStatus().equals(UserStatus.SUSPENDED))
                .build();
    }

    private List<GrantedAuthority> getAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (user.getUserType().equals(UserType.USER)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        } else authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        if (user.getRiderProfile() != null){
            authorities.add(new SimpleGrantedAuthority("ROLE_RIDER"));
        }
        if (user.getDriverProfile() != null){
            authorities.add(new SimpleGrantedAuthority("ROLE_DRIVER"));
        } //TODO: RIDER and DRIVER are ABAC permissions, not roles, fix this later
        return authorities;
    }
}
