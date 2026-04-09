package com.fintrack.dto.savings;

import com.fintrack.entity.SavingsGoal.GoalStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class SavingsDTOs {

    public record CreateGoalRequest(
            @NotBlank @Size(max = 100) String name,
            String description,
            @NotNull @Positive BigDecimal targetAmount,
            BigDecimal monthlyContribution,
            LocalDate targetDate,
            Long accountId,
            String icon,
            String color
    ) {}

    public record UpdateGoalRequest(
            @Size(max = 100) String name,
            String description,
            @Positive BigDecimal targetAmount,
            BigDecimal monthlyContribution,
            LocalDate targetDate,
            GoalStatus status,
            String icon,
            String color
    ) {}

    public record GoalResponse(
            Long id,
            String name,
            String description,
            BigDecimal targetAmount,
            BigDecimal currentAmount,
            BigDecimal remaining,
            Double percentComplete,
            BigDecimal monthlyContribution,
            LocalDate targetDate,
            Integer monthsToGoal,
            GoalStatus status,
            String icon,
            String color,
            String createdAt
    ) {}

    public record ContributeRequest(
            @NotNull @Positive BigDecimal amount,
            String notes
    ) {}

    public record ContributionResponse(
            Long id,
            BigDecimal amount,
            String notes,
            String contributedAt
    ) {}
}
