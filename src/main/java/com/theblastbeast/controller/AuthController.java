package com.theblastbeast.controller;

import com.theblastbeast.api.AuthApiDelegate;
import com.theblastbeast.domain.vo.AuthResponse;
import com.theblastbeast.domain.vo.AuthUser;
import com.theblastbeast.domain.vo.LoginRequest;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.security.AuthCookieService;
import com.theblastbeast.security.UserPrincipal;
import com.theblastbeast.service.AuthenticationService;
import com.theblastbeast.service.AuthenticationService.AuthSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthController implements AuthApiDelegate {

    private final AuthenticationService authenticationService;
    private final AuthCookieService authCookieService;

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest loginRequest) {
        log.debug("INIT - AuthController -> login() - email={}", loginRequest.getEmail());
        AuthSession session = authenticationService.login(
                loginRequest.getEmail(), loginRequest.getPassword());
        authCookieService.writeRefreshToken(session.refreshToken());
        log.debug("END - AuthController -> login() - user={}", session.user().getId());
        return ResponseEntity.ok(toResponse(session));
    }

    @Override
    public ResponseEntity<AuthResponse> refreshToken() {
        log.debug("INIT - AuthController -> refreshToken()");
        String rawToken = authCookieService.readRefreshToken()
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        AuthSession session = authenticationService.refresh(rawToken);
        authCookieService.writeRefreshToken(session.refreshToken());
        log.debug("END - AuthController -> refreshToken() - user={}", session.user().getId());
        return ResponseEntity.ok(toResponse(session));
    }

    @Override
    public ResponseEntity<Void> logout() {
        log.debug("INIT - AuthController -> logout()");
        authCookieService.readRefreshToken().ifPresent(authenticationService::logout);
        authCookieService.clearRefreshToken();
        log.debug("END - AuthController -> logout()");
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AuthUser> getCurrentUser() {
        log.debug("INIT - AuthController -> getCurrentUser()");
        UserPrincipal principal = currentPrincipal();
        ResponseEntity<AuthUser> response =
                ResponseEntity.ok(authenticationService.currentUser(principal));
        log.debug("END - AuthController -> getCurrentUser() - user={}", principal.id());
        return response;
    }

    private UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return principal;
    }

    private AuthResponse toResponse(AuthSession session) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(session.accessToken());
        response.setTokenType("Bearer");
        response.setExpiresIn((int) session.expiresIn());
        response.setUser(authenticationService.currentUser(
                new UserPrincipal(session.user().getId(), session.user().getEmail(),
                        session.user().getDisplayName(), session.user().getRole())));
        return response;
    }
}
