package com.fintrack.dto;

import com.fintrack.entity.Transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * DTO for monthly transaction summary.
 * Used for dashboard analytics charts and reports.
 * 
 * Structure:
 * - month: First day of the month (2026-04-01)
 * - type: INCOME, EXPENSE, etc.
 * - total: Sum of transactions for this month/type combination
 */
public record MonthlySummaryItem(
    LocalDate month,
    TransactionType type,
    BigDecimal total
) {
    
    public MonthlySummaryItem {
        if (month == null) {
            throw new IllegalArgumentException("Month cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }
        if (total == null) {
            throw new IllegalArgumentException("Total cannot be null");
        }
        // Ensure month is the first day
        if (month.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("Month must be first day of month (day = 1)");
        }
    }
    
    /**
     * Get the YearMonth representation for grouping operations.
     */
    public YearMonth getYearMonth() {
        return YearMonth.from(month);
    }
}
