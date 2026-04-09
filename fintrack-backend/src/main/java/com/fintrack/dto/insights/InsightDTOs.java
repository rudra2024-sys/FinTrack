package com.fintrack.dto.insights;

import java.math.BigDecimal;
import java.util.List;

public class InsightDTOs {

    public record InsightsResponse(
            BigDecimal predictedNextMonthExpense,
            BigDecimal averageMonthlyExpense,
            BigDecimal savingsPotential,
            List<AnomalyItem> anomalies,
            List<PatternItem> patterns,
            List<RecommendationItem> recommendations
    ) {}

    public record AnomalyItem(
            Long transactionId,
            String description,
            BigDecimal amount,
            String reason,
            String severity
    ) {}

    public record PatternItem(
            String title,
            String detail,
            String severity
    ) {}

    public record RecommendationItem(
            String title,
            String detail,
            String action
    ) {}
}
