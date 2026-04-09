package com.fintrack.controller;

import com.fintrack.dto.account.AccountDTOs.*;
import com.fintrack.entity.Account;
import com.fintrack.entity.Account.AccountType;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Manage bank accounts and wallets")
public class AccountController {

    private final AccountRepository accountRepository;
    private final SecurityUtils securityUtils;
    private final com.fintrack.repository.UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        Long userId = securityUtils.getCurrentUserId();
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        Account account = Account.builder()
                .user(user)
                .name(req.name())
                .type(req.type())
                .balance(req.initialBalance() != null ? req.initialBalance() : java.math.BigDecimal.ZERO)
                .currency(req.currency() != null ? req.currency() : "INR")
                .institution(req.institution())
                .color(req.color())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(accountRepository.save(account)));
    }

    @GetMapping
    @Operation(summary = "List all accounts for current user")
    public ResponseEntity<List<Response>> list() {
        Long userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                accountRepository.findByUserIdAndIsActiveTrue(userId)
                        .stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<Response> getById(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Account not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(toResponse(account));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an account")
    public ResponseEntity<Response> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateRequest req) {
        Long userId = securityUtils.getCurrentUserId();
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Account not found", HttpStatus.NOT_FOUND));

        if (req.name() != null) account.setName(req.name());
        if (req.institution() != null) account.setInstitution(req.institution());
        if (req.color() != null) account.setColor(req.color());
        if (req.isActive() != null) account.setIsActive(req.isActive());

        return ResponseEntity.ok(toResponse(accountRepository.save(account)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate an account")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Account not found", HttpStatus.NOT_FOUND));
        account.setIsActive(false);
        accountRepository.save(account);
        return ResponseEntity.noContent().build();
    }

    private Response toResponse(Account a) {
        return new Response(a.getId(), a.getName(), a.getType(), a.getBalance(),
                a.getCurrency(), a.getInstitution(), a.getColor(), a.getIsActive());
    }
}
