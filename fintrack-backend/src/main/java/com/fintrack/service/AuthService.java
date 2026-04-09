package com.fintrack.service;

import com.fintrack.dto.auth.AuthDTOs.*;
import com.fintrack.entity.*;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.*;
import com.fintrack.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ApiException("Email already in use", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .currency(req.currency() != null ? req.currency() : "INR")
                .build();

        user = userRepository.save(user);
        return buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        // Invalidate old refresh tokens
        refreshTokenRepository.deleteByUserId(user.getId());

        return buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new ApiException("Refresh token expired, please login again", HttpStatus.UNAUTHORIZED);
        }

        User user = storedToken.getUser();
        refreshTokenRepository.delete(storedToken);

        return buildTokenResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private TokenResponse buildTokenResponse(User user) {
        String accessToken = jwtUtils.generateToken(user.getEmail(), user.getId());
        String refreshToken = UUID.randomUUID().toString();

        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build();
        refreshTokenRepository.save(rt);

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtExpirationMs / 1000,
                new UserInfo(user.getId(), user.getEmail(), user.getFullName(),
                             user.getCurrency(), user.getAvatarUrl(), user.getRole())
        );
    }
}
