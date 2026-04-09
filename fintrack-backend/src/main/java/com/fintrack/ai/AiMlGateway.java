package com.fintrack.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.dto.intelligence.IntelligenceDTOs.*;
import com.fintrack.ai.categorization.RuleBasedCategorizer;
import com.fintrack.dto.insights.InsightDTOs.*;
import com.fintrack.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMlGateway {

    private final RuleBasedCategorizer ruleBasedCategorizer;

    @Value("${app.ml.enabled:false}")
    private boolean mlEnabled;

    @Value("${app.ml.base-url:http://localhost:8001}")
    private String baseUrl;

    public String categorize(String description, Transaction.TransactionType type) {
        if (mlEnabled) {
            try {
                JsonNode response = RestClient.create(baseUrl)
                        .post()
                        .uri("/ml/categorize")
                        .body(Map.of("descriptions", List.of(description), "type", type.name()))
                        .retrieve()
                        .body(JsonNode.class);
                if (response != null && response.has("categories") && response.get("categories").size() > 0) {
                    return response.get("categories").get(0).asText();
                }
            } catch (Exception ex) {
                log.warn("ML categorization unavailable, using local fallback: {}", ex.getMessage());
            }
        }
        return ruleBasedCategorizer.categorize(description, type);
    }

    public BigDecimal predictNextMonthExpense(List<BigDecimal> monthlyExpenses) {
        if (monthlyExpenses == null || monthlyExpenses.isEmpty()) {
            return BigDecimal.ZERO;
        }

        if (mlEnabled) {
            try {
                JsonNode response = RestClient.create(baseUrl)
                        .post()
                        .uri("/ml/predict")
                        .body(Map.of("series", monthlyExpenses))
                        .retrieve()
                        .body(JsonNode.class);
                if (response != null && response.has("predicted")) {
                    return response.get("predicted").decimalValue();
                }
            } catch (Exception ex) {
                log.warn("ML prediction unavailable, using local fallback: {}", ex.getMessage());
            }
        }

        if (monthlyExpenses.size() == 1) {
            return monthlyExpenses.get(0);
        }

        BigDecimal slope = BigDecimal.ZERO;
        for (int i = 1; i < monthlyExpenses.size(); i++) {
            slope = slope.add(monthlyExpenses.get(i).subtract(monthlyExpenses.get(i - 1)));
        }
        slope = slope.divide(BigDecimal.valueOf(monthlyExpenses.size() - 1L), 2, RoundingMode.HALF_UP);
        return monthlyExpenses.get(monthlyExpenses.size() - 1).add(slope).max(BigDecimal.ZERO);
    }

    public List<AnomalyItem> detectAnomalies(List<Transaction> transactions) {
        List<AnomalyItem> anomalies = new ArrayList<>();
        if (transactions == null || transactions.size() < 3) {
            return anomalies;
        }

        BigDecimal average = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);

        BigDecimal varianceAccumulator = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            BigDecimal delta = transaction.getAmount().subtract(average);
            varianceAccumulator = varianceAccumulator.add(delta.multiply(delta));
        }
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(
                varianceAccumulator.divide(BigDecimal.valueOf(transactions.size()), 4, RoundingMode.HALF_UP).doubleValue()
        ));
        BigDecimal threshold = average.add(stdDev.multiply(BigDecimal.valueOf(2)));

        transactions.stream()
                .filter(tx -> tx.getAmount().compareTo(threshold) > 0)
                .sorted(Comparator.comparing(Transaction::getAmount).reversed())
                .limit(5)
                .forEach(tx -> anomalies.add(new AnomalyItem(
                        tx.getId(),
                        tx.getDescription(),
                        tx.getAmount(),
                        "Expense is significantly above your recent average",
                        tx.getAmount().compareTo(average.multiply(BigDecimal.valueOf(3))) > 0 ? "HIGH" : "MEDIUM"
                )));

        return anomalies;
    }

    public List<PatternItem> summarizePatterns(
            Map<String, BigDecimal> categoryTotals,
            List<Object[]> topMerchants,
            BigDecimal totalExpenses,
            BigDecimal predictedNextMonthExpense
    ) {
        List<PatternItem> patterns = new ArrayList<>();
        if (totalExpenses.compareTo(BigDecimal.ZERO) <= 0) {
            patterns.add(new PatternItem("No spend profile yet", "Upload statements or add transactions to unlock pattern analysis.", "INFO"));
            return patterns;
        }

        categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> {
                    BigDecimal pct = entry.getValue()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalExpenses, 1, RoundingMode.HALF_UP);
                    patterns.add(new PatternItem(
                            "Category concentration",
                            entry.getKey() + " accounts for " + pct + "% of your recent expenses.",
                            pct.compareTo(BigDecimal.valueOf(35)) >= 0 ? "WARNING" : "INFO"
                    ));
                });

        if (topMerchants != null && !topMerchants.isEmpty()) {
            Object[] merchant = topMerchants.get(0);
            patterns.add(new PatternItem(
                    "Frequent merchant",
                    merchant[0] + " is your highest-spend merchant in the current analysis window.",
                    "INFO"
            ));
        }

        patterns.add(new PatternItem(
                "Expense forecast",
                "Projected expense next month is approximately ₹" + predictedNextMonthExpense.setScale(0, RoundingMode.HALF_UP),
                "INFO"
        ));
        return patterns;
    }

    public List<RecommendationItem> generateRecommendations(
            Map<String, BigDecimal> categoryTotals,
            BigDecimal totalExpenses,
            BigDecimal predictedNextMonthExpense,
            BigDecimal monthlyBudget,
            BigDecimal savingsGoal
    ) {
        List<RecommendationItem> recommendations = new ArrayList<>();
        if (totalExpenses.compareTo(BigDecimal.ZERO) <= 0) {
            recommendations.add(new RecommendationItem("Start data collection", "Upload at least one bank statement to unlock budgeting and AI insights.", "UPLOAD_STATEMENT"));
            return recommendations;
        }

        Map.Entry<String, BigDecimal> topCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (topCategory != null) {
            BigDecimal tenPercent = topCategory.getValue().multiply(BigDecimal.valueOf(0.10)).setScale(0, RoundingMode.HALF_UP);
            recommendations.add(new RecommendationItem(
                    "Trim your top category",
                    "Reducing " + topCategory.getKey() + " spending by 10% can save about ₹" + tenPercent + " per month.",
                    "REVIEW_CATEGORY"
            ));
        }

        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0
                && predictedNextMonthExpense.compareTo(monthlyBudget) > 0) {
            recommendations.add(new RecommendationItem(
                    "Budget breach risk",
                    "Your projected expense exceeds the monthly budget by ₹" + predictedNextMonthExpense.subtract(monthlyBudget).setScale(0, RoundingMode.HALF_UP) + ".",
                    "TIGHTEN_BUDGET"
            ));
        }

        if (savingsGoal != null && savingsGoal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal savingsPotential = monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0
                    ? monthlyBudget.subtract(predictedNextMonthExpense).max(BigDecimal.ZERO)
                    : totalExpenses.multiply(BigDecimal.valueOf(0.05)).setScale(0, RoundingMode.HALF_UP);
            recommendations.add(new RecommendationItem(
                    "Savings runway",
                    "At your current pace, a monthly surplus of about ₹" + savingsPotential + " can accelerate your savings goal.",
                    "BOOST_SAVINGS"
            ));
        }

        return recommendations;
    }

    public Map<String, BigDecimal> aggregateCategoryTotals(List<Object[]> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        if (rows == null) {
            return totals;
        }
        for (Object[] row : rows) {
            totals.put(String.valueOf(row[0]), (BigDecimal) row[2]);
        }
        return totals;
    }

    public IntelligenceResponse analyzeFinancialIntelligence(MlAnalyzeRequest request) {
        if (!mlEnabled) {
            throw new IllegalStateException("ML service is disabled");
        }
        return RestClient.create(baseUrl)
                .post()
                .uri("/ml/intelligence")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(IntelligenceResponse.class);
    }

    public PdfIntelligenceResponse analyzePdfStatement(
            MultipartFile file,
            Double incomeStability,
            Double savingsRate,
            Double debtPressure
    ) {
        if (!mlEnabled) {
            throw new IllegalStateException("ML service is disabled");
        }
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            if (incomeStability != null) {
                body.add("income_stability", incomeStability);
            }
            if (savingsRate != null) {
                body.add("savings_rate", savingsRate);
            }
            if (debtPressure != null) {
                body.add("debt_pressure", debtPressure);
            }

            return RestClient.create(baseUrl)
                    .post()
                    .uri("/ml/pdf/intelligence")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(PdfIntelligenceResponse.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to forward PDF to ML service", ex);
        }
    }

    public PdfExtractionResponse extractPdfStatement(MultipartFile file) {
        if (!mlEnabled) {
            throw new IllegalStateException("ML service is disabled");
        }
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            return RestClient.create(baseUrl)
                    .post()
                    .uri("/ml/pdf/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(PdfExtractionResponse.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to forward PDF to ML extraction service", ex);
        }
    }
}
