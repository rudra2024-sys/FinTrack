package com.fintrack.controller;

import com.fintrack.dto.recurring.RecurringDTOs.*;
import com.fintrack.security.SecurityUtils;
import com.fintrack.service.RecurringTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/recurring-transactions")
@RequiredArgsConstructor
@Tag(name = "Recurring Transactions", description = "Manage recurring income and expenses")
public class RecurringTransactionController {

    private final RecurringTransactionService recurringService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recurringService.create(securityUtils.getCurrentUserId(), req));
    }

    @GetMapping
    public ResponseEntity<List<Response>> list() {
        return ResponseEntity.ok(recurringService.findAll(securityUtils.getCurrentUserId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Response> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateRequest req) {
        return ResponseEntity.ok(recurringService.update(id, securityUtils.getCurrentUserId(), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recurringService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
