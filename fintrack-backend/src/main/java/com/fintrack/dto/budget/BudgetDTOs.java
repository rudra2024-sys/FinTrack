package com.fintrack.dto.budget;

import com.fintrack.entity.Budget.BudgetPeriod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class BudgetDTOs {

    public record CreateRequest(
            Long categoryId,
            @NotBlank @Size(max = 100) String name,
            @NotNull @Positive BigDecimal amount,
            @NotNull BudgetPeriod period,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal alertThreshold
    ) {}

    public record UpdateRequest(
            @Size(max = 100) String name,
            @Positive BigDecimal amount,
            BudgetPeriod period,
            LocalDate endDate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal alertThreshold,
            Boolean isActive
    ) {}

    public record Response(
            Long id,
            Long categoryId,
            String categoryName,
            String categoryColor,
            String name,
            BigDecimal amount,
            BigDecimal spent,
            BigDecimal remaining,
            Double percentUsed,
            BudgetPeriod period,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal alertThreshold,
            Boolean isActive,
            Boolean alertTriggered
    ) {}
}
