package ru.ticket.auth.service;

import ru.ticket.auth.dto.AuthDtos.AuthResponse;
import ru.ticket.auth.dto.AuthDtos.RegisterRequest;
import ru.ticket.auth.dto.AuthDtos.UserDto;

public interface AuthService {

    AuthResponse register(RegisterRequest req);

    AuthResponse login(String email, String password);

    /** Обновление access-токена по refresh-токену. */
    AuthResponse refresh(String refreshToken);

    /**
     * Выпуск нового refresh-токена для УЖЕ проверенного пользователя.
     * Пароль не проверяется — вызывать только после успешной аутентификации
     * (login / register / refresh / OAuth).
     */
    String generateRefreshToken(String userId);

    UserDto currentUser(String userId);
}