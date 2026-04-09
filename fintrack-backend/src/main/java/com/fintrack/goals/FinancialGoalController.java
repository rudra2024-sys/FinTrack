package com.fintrack.goals;

import com.fintrack.dto.goals.FinancialGoalDTOs.*;
import com.fintrack.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
@Tag(name = "Financial Goals", description = "User financial setup including rent, savings target, and monthly budget")
public class FinancialGoalController {

    private final FinancialGoalService financialGoalService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Create or update the user's financial goal profile")
    public ResponseEntity<Response> upsert(@Valid @RequestBody UpsertRequest request) {
        return ResponseEntity.ok(financialGoalService.upsert(securityUtils.getCurrentUserId(), request));
    }

    @GetMapping
    @Operation(summary = "Get the current user's financial goal profile")
    public ResponseEntity<Response> get() {
        return ResponseEntity.ok(financialGoalService.get(securityUtils.getCurrentUserId()));
    }
}

