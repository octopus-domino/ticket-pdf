package ru.ticket.auth.service;

import ru.ticket.auth.dto.AuthDtos.AuthResponse;
import ru.ticket.auth.dto.AuthDtos.RegisterRequest;
import ru.ticket.auth.dto.AuthDtos.UserDto;

public interface AuthService {
    AuthResponse register(RegisterRequest req);
    AuthResponse login(String email, String password);
    AuthResponse refresh(String refreshToken);
    String issueRefreshToken(String email, String password);
    UserDto currentUser(String userId);
}