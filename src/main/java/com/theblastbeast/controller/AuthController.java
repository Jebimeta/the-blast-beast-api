package com.theblastbeast.controller;

import com.theblastbeast.api.AuthApiDelegate;
import com.theblastbeast.domain.vo.AuthResponse;
import com.theblastbeast.domain.vo.AuthUser;
import com.theblastbeast.domain.vo.ChangePasswordRequest;
import com.theblastbeast.domain.vo.LoginRequest;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.security.AuthCookieService;
import com.theblastbeast.security.LoginRateLimiter;
import com.theblastbeast.security.UserPrincipal;
import com.theblastbeast.service.AuthenticationService;
import com.theblastbeast.service.AuthenticationService.AuthSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthController implements AuthApiDelegate {

    private final AuthenticationService authenticationService;
    private final AuthCookieService authCookieService;
    private final LoginRateLimiter loginRateLimiter;

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest loginRequest) {
        String clientIp = clientIp();
        loginRateLimiter.check(clientIp, loginRequest.getEmail());
        log.debug("INIT - AuthController -> login() - email={}", loginRequest.getEmail());
        try {
            AuthSession session = authenticationService.login(
                    loginRequest.getEmail(), loginRequest.getPassword());
            loginRateLimiter.reset(clientIp, loginRequest.getEmail());
            authCookieService.writeRefreshToken(session.refreshToken());
            log.debug("END - AuthController -> login() - user={}", session.user().getId());
            return ResponseEntity.ok(toResponse(session));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.INVALID_CREDENTIALS
                    || exception.getErrorCode() == ErrorCode.USER_DISABLED) {
                loginRateLimiter.recordFailure(clientIp, loginRequest.getEmail());
            }
            throw exception;
        }
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

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest changePasswordRequest) {
        log.debug("INIT - AuthController -> changePassword()");
        UserPrincipal principal = currentPrincipal();
        authenticationService.changePassword(principal,
                changePasswordRequest.getCurrentPassword(), changePasswordRequest.getNewPassword());
        // Al cambiar la contraseña se revocan los refrescos y se borra la cookie:
        // el usuario vuelve a iniciar sesión con la contraseña nueva.
        authCookieService.clearRefreshToken();
        log.debug("END - AuthController -> changePassword() - user={}", principal.id());
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return principal;
    }

    /**
     * IP del cliente tal y como la ve el contenedor. Caddy sobrescribe
     * {@code X-Forwarded-For} con la IP real y Spring la aplica a
     * {@code getRemoteAddr()} mediante {@code forward-headers-strategy: framework}.
     *
     * <p>No se inyecta {@code HttpServletRequest} en el constructor para que el
     * perfil {@code reset-password}, que no levanta un contexto web, pueda
     * cargar este bean.
     */
    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String remoteAddr = servletAttributes.getRequest().getRemoteAddr();
            if (remoteAddr != null && !remoteAddr.isBlank()) {
                return remoteAddr;
            }
        }
        return "unknown";
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
