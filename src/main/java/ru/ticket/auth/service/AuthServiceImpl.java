package ru.ticket.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.auth.domain.Role;
import ru.ticket.auth.domain.User;
import ru.ticket.auth.dto.AuthDtos.AuthResponse;
import ru.ticket.auth.dto.AuthDtos.RegisterRequest;
import ru.ticket.auth.dto.AuthDtos.UserDto;
import ru.ticket.auth.repository.UserRepository;
import ru.ticket.auth.security.JwtService;
import ru.ticket.common.ApiException;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Email already registered");
        }
        String hash = passwordEncoder.encode(req.password());
        String displayName = (req.name() == null || req.name().isBlank())
                ? req.email().split("@")[0]
                : req.name().trim();
        User user = users.save(new User(req.email().toLowerCase(), hash, displayName, Role.USER));
        return new AuthResponse(jwtService.generateAccessToken(user), toDto(user));
    }

    @Override
    public AuthResponse login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new AuthResponse(jwtService.generateAccessToken(user), toDto(user));
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        try {
            Claims claims = jwtService.parse(refreshToken);
            if (!"refresh".equals(claims.get("kind", String.class))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Not a refresh token");
            }
            User user = users.findById(claims.getSubject())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
            return new AuthResponse(jwtService.generateAccessToken(user), toDto(user));
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    @Override
    public String issueRefreshToken(String email, String password) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return jwtService.generateRefreshToken(user);
    }

    @Override
    public UserDto currentUser(String userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        return toDto(user);
    }

    private UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getName());
    }
}