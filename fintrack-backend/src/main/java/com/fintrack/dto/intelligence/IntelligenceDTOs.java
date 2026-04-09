package com.fintrack.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class IntelligenceDTOs {

    public record AnalyzeRequest(
            LocalDate startDate,
            LocalDate endDate,
            Double incomeStability,
            Double savingsRate,
            Double debtPressure
    ) {}

    public record MlTransaction(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate date,
            String time,
            String transaction_type,
            String merchant_person,
            BigDecimal amount,
            String description,
            String category,
            Double confidence
    ) {}

    public record MlAnalyzeRequest(
            List<MlTransaction> transactions,
            Double income_stability,
            Double savings_rate,
            Double debt_pressure
    ) {}

    public record TrendPointResponse(
            String period,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net
    ) {}

    public record CategoryDistributionItem(
            String category,
            BigDecimal amount,
            Double confidence
    ) {}

    public record HiddenStatePointResponse(
            String date,
            BigDecimal expense,
            String hidden_state,
            boolean anomaly,
            Double anomaly_score
    ) {}

    public record RiskAssessmentResponse(
            Double income_stability,
            Double expense_level,
            Double savings_rate,
            Double debt_pressure,
            String financial_risk,
            Double risk_score,
            String recommendation
    ) {}

    public record AlertResponse(
            String level,
            String title,
            String detail
    ) {}

    public record IntelligenceResponse(
            Integer extracted_transaction_count,
            List<TrendPointResponse> daily_trends,
            List<TrendPointResponse> weekly_trends,
            List<CategoryDistributionItem> category_distribution,
            List<HiddenStatePointResponse> hidden_state_timeline,
            RiskAssessmentResponse risk_assessment,
            List<String> insights,
            List<AlertResponse> alerts
    ) {}

    public record PdfExtractionResponse(
            String source,
            Integer transaction_count,
            List<MlTransaction> transactions
    ) {}

    public record PdfIntelligenceResponse(
            PdfExtractionResponse extraction,
            IntelligenceResponse intelligence
    ) {}
}
