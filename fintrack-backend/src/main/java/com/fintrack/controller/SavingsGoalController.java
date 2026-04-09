package com.fintrack.controller;

import com.fintrack.dto.savings.SavingsDTOs.*;
import com.fintrack.security.SecurityUtils;
import com.fintrack.service.SavingsGoalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/savings-goals")
@RequiredArgsConstructor
@Tag(name = "Savings Goals", description = "Manage savings goals and contributions")
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody CreateGoalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savingsGoalService.create(securityUtils.getCurrentUserId(), req));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> list() {
        return ResponseEntity.ok(savingsGoalService.findAll(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(savingsGoalService.findById(id, securityUtils.getCurrentUserId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GoalResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateGoalRequest req) {
        return ResponseEntity.ok(savingsGoalService.update(id, securityUtils.getCurrentUserId(), req));
    }

    @PostMapping("/{id}/contribute")
    public ResponseEntity<GoalResponse> contribute(@PathVariable Long id,
                                                    @Valid @RequestBody ContributeRequest req) {
        return ResponseEntity.ok(savingsGoalService.contribute(id, securityUtils.getCurrentUserId(), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        savingsGoalService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
