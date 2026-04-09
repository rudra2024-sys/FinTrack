package com.fintrack.controller;

import com.fintrack.entity.Category;
import com.fintrack.entity.Category.CategoryType;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "System and custom transaction categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    public record CategoryRequest(
            @NotBlank String name,
            String icon,
            String color,
            @NotNull CategoryType type
    ) {}

    public record CategoryResponse(
            Long id, String name, String icon, String color,
            CategoryType type, Boolean isSystem
    ) {}

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list() {
        Long userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                categoryRepository.findAllForUser(userId)
                        .stream().map(this::toResponse).toList()
        );
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest req) {
        Long userId = securityUtils.getCurrentUserId();
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        Category category = Category.builder()
                .user(user)
                .name(req.name())
                .icon(req.icon())
                .color(req.color())
                .type(req.type())
                .isSystem(false)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(categoryRepository.save(category)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Category not found or is a system category", HttpStatus.NOT_FOUND));
        categoryRepository.delete(category);
        return ResponseEntity.noContent().build();
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getIcon(),
                c.getColor(), c.getType(), c.getIsSystem());
    }
}
