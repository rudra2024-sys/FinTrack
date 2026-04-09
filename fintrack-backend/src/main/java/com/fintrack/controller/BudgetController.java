package com.fintrack.controller;

import com.fintrack.dto.budget.BudgetDTOs.*;
import com.fintrack.security.SecurityUtils;
import com.fintrack.service.BudgetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Manage spending budgets and alerts")
public class BudgetController {

    private final BudgetService budgetService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetService.create(securityUtils.getCurrentUserId(), req));
    }

    @GetMapping
    public ResponseEntity<List<Response>> list() {
        return ResponseEntity.ok(budgetService.findAll(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(budgetService.findById(id, securityUtils.getCurrentUserId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Response> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateRequest req) {
        return ResponseEntity.ok(budgetService.update(id, securityUtils.getCurrentUserId(), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        budgetService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
