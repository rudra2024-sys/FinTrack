package com.fintrack.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDTOs {

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
            @NotBlank @Size(min = 2, max = 100) String fullName,
            String currency
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserInfo user
    ) {
        public static TokenResponse of(String access, String refresh, long expiresIn, UserInfo user) {
            return new TokenResponse(access, refresh, "Bearer", expiresIn, user);
        }
    }

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record UserInfo(
            Long id,
            String email,
            String fullName,
            String currency,
            String avatarUrl,
            String role
    ) {}
}
