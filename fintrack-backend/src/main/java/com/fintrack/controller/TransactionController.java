package com.fintrack.controller;

import com.fintrack.dto.transaction.TransactionDTOs.*;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.security.SecurityUtils;
import com.fintrack.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Create, read, update, delete transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(securityUtils.getCurrentUserId(), req));
    }

    @GetMapping
    @Operation(summary = "List transactions with optional filters")
    public ResponseEntity<PagedResponse> list(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        FilterRequest filter = new FilterRequest(type, categoryId, accountId, startDate, endDate, search, page, size);
        return ResponseEntity.ok(transactionService.findAll(securityUtils.getCurrentUserId(), filter));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transaction by ID")
    public ResponseEntity<Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.findById(id, securityUtils.getCurrentUserId()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a transaction")
    public ResponseEntity<Response> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateRequest req) {
        return ResponseEntity.ok(transactionService.update(id, securityUtils.getCurrentUserId(), req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
