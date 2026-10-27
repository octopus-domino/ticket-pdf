package ru.ticket.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.dto.AuthDtos.AuthResponse;
import ru.ticket.auth.dto.AuthDtos.LoginRequest;
import ru.ticket.auth.dto.AuthDtos.RegisterRequest;
import ru.ticket.auth.dto.AuthDtos.UserDto;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.auth.service.AuthService;
import ru.ticket.common.ApiException;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                                 HttpServletResponse response) {
        AuthResponse auth = authService.register(req);
        String refresh = authService.issueRefreshToken(req.email(), req.password());
        setRefreshCookie(response, refresh);
        return ResponseEntity.status(HttpStatus.CREATED).body(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletResponse response) {
        AuthResponse auth = authService.login(req.email(), req.password());
        String refresh = authService.issueRefreshToken(req.email(), req.password());
        setRefreshCookie(response, refresh);
        return ResponseEntity.ok(auth);
    }

    /**
     * Обновление access-токена по refresh-cookie.
     * НЕ пытаемся ротировать refresh и НЕ вызываем issueRefreshToken —
     * это ломало вход после перезагрузки (падало на неверном пароле "").
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            AuthResponse auth = authService.refresh(refreshToken);
            return ResponseEntity.ok(auth);
        } catch (ApiException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserIdPrincipal principal) {
        return authService.currentUser(principal.userId());
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(false)          // на проде — true (HTTPS)
                .sameSite("Lax")        // работает, т.к. фронт и бэк за одним origin через proxy
                .path("/api/auth")
                .maxAge(REFRESH_COOKIE_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(false).sameSite("Lax").path("/api/auth").maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}