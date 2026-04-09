package com.fintrack.insights;

import com.fintrack.ai.AiMlGateway;
import com.fintrack.dto.insights.InsightDTOs.*;
import com.fintrack.entity.FinancialGoal;
import com.fintrack.entity.Transaction;
import com.fintrack.goals.FinancialGoalService;
import com.fintrack.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InsightService {

    private final TransactionRepository transactionRepository;
    private final AiMlGateway aiMlGateway;
    private final FinancialGoalService financialGoalService;

    @Transactional(readOnly = true)
    public InsightsResponse getInsights(Long userId) {
        LocalDate referenceDate = LocalDate.now();
        LocalDate latestTransactionDate = transactionRepository.findLatestTransactionDateByUserId(userId);
        if (latestTransactionDate != null) {
            LocalDate currentMonthStart = referenceDate.withDayOfMonth(1);
            BigDecimal currentMonthIncome = transactionRepository.sumIncomeByUserAndDateRange(userId, currentMonthStart, referenceDate);
            BigDecimal currentMonthExpenses = transactionRepository.sumExpensesByUserAndDateRange(userId, currentMonthStart, referenceDate);
            if (currentMonthIncome.compareTo(BigDecimal.ZERO) == 0 && currentMonthExpenses.compareTo(BigDecimal.ZERO) == 0) {
                referenceDate = latestTransactionDate;
            }
        }

        LocalDate sixMonthsAgo = referenceDate.minusMonths(5).withDayOfMonth(1);
        LocalDate currentMonthStart = referenceDate.withDayOfMonth(1);

        List<Object[]> categoryRows = transactionRepository.getCategoryBreakdown(userId, sixMonthsAgo, referenceDate);
        Map<String, BigDecimal> categoryTotals = aiMlGateway.aggregateCategoryTotals(categoryRows);
        List<Object[]> topMerchants = transactionRepository.getTopExpenseMerchants(userId, sixMonthsAgo, referenceDate);
        List<Transaction> recentExpenses = transactionRepository.findTop10ByUserIdAndTypeOrderByTransactionDateDescCreatedAtDesc(
                userId, Transaction.TransactionType.EXPENSE
        );

        List<BigDecimal> monthlyExpenses = buildMonthlyExpenseSeries(userId, referenceDate);
        BigDecimal predictedNextMonthExpense = aiMlGateway.predictNextMonthExpense(monthlyExpenses);
        BigDecimal averageMonthlyExpense = monthlyExpenses.isEmpty()
                ? BigDecimal.ZERO
                : monthlyExpenses.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(monthlyExpenses.size()), 2, RoundingMode.HALF_UP);
        BigDecimal currentMonthExpenses = transactionRepository.sumExpensesByUserAndDateRange(userId, currentMonthStart, referenceDate);

        FinancialGoal goal = financialGoalService.findOptional(userId);
        BigDecimal monthlyBudget = goal != null ? goal.getMonthlyBudget() : null;
        BigDecimal savingsGoal = goal != null ? goal.getSavingsGoal() : null;
        BigDecimal savingsPotential = monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0
                ? monthlyBudget.subtract(currentMonthExpenses).max(BigDecimal.ZERO)
                : averageMonthlyExpense.multiply(BigDecimal.valueOf(0.05)).setScale(0, RoundingMode.HALF_UP);

        List<AnomalyItem> anomalies = aiMlGateway.detectAnomalies(recentExpenses);
        List<PatternItem> patterns = aiMlGateway.summarizePatterns(
                categoryTotals,
                topMerchants,
                currentMonthExpenses.max(averageMonthlyExpense),
                predictedNextMonthExpense
        );
        List<RecommendationItem> recommendations = aiMlGateway.generateRecommendations(
                categoryTotals,
                currentMonthExpenses.max(averageMonthlyExpense),
                predictedNextMonthExpense,
                monthlyBudget,
                savingsGoal
        );

        if (goal != null && goal.getRent() != null && goal.getRent().compareTo(BigDecimal.ZERO) > 0) {
            patterns.add(0, new PatternItem(
                    "Fixed obligation",
                    "Recorded rent target is ₹" + goal.getRent().setScale(0, RoundingMode.HALF_UP) + " per month.",
                    "INFO"
            ));
        }

        return new InsightsResponse(
                predictedNextMonthExpense.setScale(2, RoundingMode.HALF_UP),
                averageMonthlyExpense.setScale(2, RoundingMode.HALF_UP),
                savingsPotential.setScale(2, RoundingMode.HALF_UP),
                anomalies,
                patterns,
                recommendations
        );
    }

    private List<BigDecimal> buildMonthlyExpenseSeries(Long userId, LocalDate now) {
        Map<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.from(now.minusMonths(i));
            monthlyTotals.put(month, transactionRepository.sumExpensesByUserAndDateRange(userId, month.atDay(1), month.atEndOfMonth()));
        }
        return new ArrayList<>(monthlyTotals.values());
    }
}
