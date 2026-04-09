package com.fintrack.controller;

import com.fintrack.dto.auth.AuthDTOs.UserInfo;
import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile management")
public class UserProfileController {

    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "Get current user's profile")
    public ResponseEntity<UserProfileDTO> getProfile() {
        Long userId = securityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));

        return ResponseEntity.ok(new UserProfileDTO(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getCurrency(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        ));
    }

    @PutMapping
    @Operation(summary = "Update user profile (e.g., fullName, currency)")
    public ResponseEntity<UserProfileDTO> updateProfile(@RequestBody UpdateProfileRequest req) {
        Long userId = securityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));

        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName());
        }
        if (req.currency() != null && !req.currency().isBlank()) {
            user.setCurrency(req.currency());
        }

        User updated = userRepository.save(user);

        return ResponseEntity.ok(new UserProfileDTO(
                updated.getId(),
                updated.getEmail(),
                updated.getFullName(),
                updated.getCurrency(),
                updated.getCreatedAt(),
                updated.getUpdatedAt()
        ));
    }

    // DTO Records for API
    public record UserProfileDTO(
            Long id,
            String email,
            String fullName,
            String currency,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdateProfileRequest(
            String fullName,
            String currency
    ) {}
}
