package com.fintrack.controller;

import com.fintrack.dto.auth.AuthDTOs.*;
import com.fintrack.security.SecurityUtils;
import com.fintrack.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh")
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh tokens")
    public ResponseEntity<Void> logout() {
        authService.logout(securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
