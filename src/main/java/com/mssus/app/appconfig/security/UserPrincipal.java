package com.mssus.app.appconfig.security;

import lombok.Getter;
import org.springframework.security.core.userdetails.UserDetails;
import java.security.Principal;

@Getter
public class UserPrincipal implements Principal {
    private final UserDetails userDetails;

    public UserPrincipal(UserDetails userDetails) {
        this.userDetails = userDetails;
    }

    @Override
    public String getName() {
        return userDetails.getUsername();
    }

    public UserDetails getUserDetails() {
        return userDetails;
    }
}