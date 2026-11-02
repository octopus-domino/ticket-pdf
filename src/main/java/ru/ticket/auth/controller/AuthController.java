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
        // Пароль уже проверен внутри register() — генерируем refresh для user.id,
        // без повторного bcrypt.
        String refresh = authService.generateRefreshToken(auth.user().id());
        setRefreshCookie(response, refresh);
        return ResponseEntity.status(HttpStatus.CREATED).body(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletResponse response) {
        AuthResponse auth = authService.login(req.email(), req.password());
        // Пароль проверен в login() — второй bcrypt не нужен.
        String refresh = authService.generateRefreshToken(auth.user().id());
        setRefreshCookie(response, refresh);
        return ResponseEntity.ok(auth);
    }

    /**
     * Обновление access-токена + ротация refresh-cookie.
     *
     * Ротация: выдаём НОВЫЙ refresh при каждом /refresh. Это закрывает
     * сценарий «refresh утёк — им можно пользоваться 7 дней». После ротации
     * старый refresh формально ещё валиден (нет blacklist), но браузер
     * заменяет cookie новым, и старая копия живёт только у атакующего —
     * которого можно вычислить по повторному использованию.
     *
     * ПОЛНОЦЕННЫЙ single-use + blacklist — отдельная задача (нужен
     * персистентный store). Здесь — базовый шаг.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            AuthResponse auth = authService.refresh(refreshToken);
            // Ротация: старый refresh инвалидируется в браузере при первом же
            // успешном ответе. Табы, которые гонятся за один /refresh,
            // получат каждый свой новый refresh — последний выиграет cookie,
            // оба access-токена рабочие (см. grace-период в JwtService, если
            // понадобится ограничить).
            String newRefresh = authService.generateRefreshToken(auth.user().id());
            setRefreshCookie(response, newRefresh);
            return ResponseEntity.ok(auth);
        } catch (ApiException e) {
            clearRefreshCookie(response);
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
                // ВНИМАНИЕ: secure(false) — только для локальной разработки
                // по HTTP. В prod это должно читаться из app.cookie-secure
                // (это B4, отдельная задача).
                .secure(false)
                .sameSite("Lax")
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