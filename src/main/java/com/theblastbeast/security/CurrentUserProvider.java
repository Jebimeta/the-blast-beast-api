package com.theblastbeast.security;

import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public UserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return principal;
    }

    public Long id() {
        return principal().id();
    }
}
