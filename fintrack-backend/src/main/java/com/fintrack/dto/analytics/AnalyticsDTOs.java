package com.fintrack.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public class AnalyticsDTOs {

    public record DashboardSummary(
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netSavings,
            BigDecimal savingsRate,
            Long transactionCount,
            BigDecimal highestExpense,
            BigDecimal totalBalance,
            List<AccountBalance> accountBalances,
            List<MonthlyData> monthlyTrend,
            List<CategorySpend> topCategories,
            List<CategorySpend> categoryDistribution,
            SpendingStateSummary spendingStates,
            List<BudgetAlert> budgetAlerts
    ) {}

    public record AccountBalance(
            Long id,
            String name,
            String type,
            BigDecimal balance,
            String currency,
            String color
    ) {}

    public record MonthlyData(
            String month,           // "2025-01"
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal net
    ) {}

    public record CategorySpend(
            String category,
            String color,
            BigDecimal amount,
            Double percentage
    ) {}

    public record SpendingStateSummary(
            long low,
            long normal,
            long high
    ) {}

    public record BudgetAlert(
            Long budgetId,
            String budgetName,
            BigDecimal budgetAmount,
            BigDecimal spent,
            Double percentUsed,
            String severity         // "WARNING" | "EXCEEDED"
    ) {}

    public record InsightResponse(
            String type,            // "SPENDING_UP", "GOAL_ON_TRACK", "ANOMALY", etc.
            String message,
            String severity         // "INFO", "WARNING", "POSITIVE"
    ) {}
}
