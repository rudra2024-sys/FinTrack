package com.fintrack.dto.goals;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;

public class FinancialGoalDTOs {

    public record UpsertRequest(
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal rent,
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal savingsGoal,
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal monthlyBudget,
            String notes
    ) {}

    public record Response(
            Long id,
            BigDecimal rent,
            BigDecimal savingsGoal,
            BigDecimal monthlyBudget,
            String notes,
            Instant updatedAt
    ) {}
}

