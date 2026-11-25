package ru.ticket.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() { }

    public record RegisterRequest(
            @Email @NotBlank @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 100) String name
    ) { }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) { }

    public record UserDto(String id, String email, String name) { }

    public record AuthResponse(String accessToken, UserDto user) { }
}