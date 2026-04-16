package com.fintrack.service;

import com.fintrack.dto.analytics.AnalyticsDTOs.*;
import com.fintrack.entity.Transaction;
import com.fintrack.dto.budget.BudgetDTOs;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BudgetService budgetService;
    private final TransactionClassificationService transactionClassificationService;

    @Transactional(readOnly = true)
    public DashboardSummary getDashboard(Long userId) {
        LocalDate now = LocalDate.now();
        LocalDate referenceDate = now;
        LocalDate monthStart = referenceDate.withDayOfMonth(1);
        LocalDate monthEnd = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());

        // This month income & expenses
        BigDecimal income = transactionRepository.sumByUserAndTypeAndDateRange(
                userId, TransactionType.INCOME, monthStart, monthEnd);
        BigDecimal expenses = transactionRepository.sumByUserAndTypeAndDateRange(
                userId, TransactionType.EXPENSE, monthStart, monthEnd);

        // If the current month is empty, use the latest month with imported activity.
        if (income.compareTo(BigDecimal.ZERO) == 0 && expenses.compareTo(BigDecimal.ZERO) == 0) {
            LocalDate latestTransactionDate = transactionRepository.findLatestTransactionDateByUserId(userId);
            if (latestTransactionDate != null) {
                referenceDate = latestTransactionDate;
                monthStart = referenceDate.withDayOfMonth(1);
                monthEnd = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());
                income = transactionRepository.sumByUserAndTypeAndDateRange(
                        userId, TransactionType.INCOME, monthStart, monthEnd);
                expenses = transactionRepository.sumByUserAndTypeAndDateRange(
                        userId, TransactionType.EXPENSE, monthStart, monthEnd);
            }
        }

        BigDecimal net = income.subtract(expenses);
        BigDecimal savingsRate = income.compareTo(BigDecimal.ZERO) > 0
                ? net.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Account balances
        List<AccountBalance> accountBalances = accountRepository
                .findByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(a -> new AccountBalance(a.getId(), a.getName(), a.getType().name(),
                        a.getBalance(), a.getCurrency(), a.getColor()))
                .toList();

        BigDecimal totalBalance = accountBalances.stream()
                .map(AccountBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 12-month trend
        List<MonthlyData> monthlyTrend = getMonthlyTrend(userId, referenceDate);

        // Top spending categories this month
        List<CategorySpend> topCategories = getCategoryBreakdown(userId, monthStart, monthEnd);
        List<Transaction> monthlyTransactions = transactionRepository
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(userId, monthStart, monthEnd);
        long transactionCount = monthlyTransactions.size();
        BigDecimal highestExpense = monthlyTransactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        SpendingStateSummary spendingStates = summarizeSpendingStates(monthlyTransactions);

        // Budget alerts
        List<BudgetAlert> budgetAlerts = getBudgetAlerts(userId);

        return new DashboardSummary(income, expenses, net, savingsRate,
                transactionCount, highestExpense, totalBalance, accountBalances,
                monthlyTrend, topCategories, topCategories, spendingStates, budgetAlerts);
    }

    @Transactional(readOnly = true)
    public List<MonthlyData> getMonthlyTrend(Long userId) {
        return getMonthlyTrend(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<MonthlyData> getMonthlyTrend(Long userId, LocalDate referenceDate) {
        LocalDate endMonth = referenceDate.withDayOfMonth(1);
        LocalDate startDate = endMonth.minusMonths(11);
        
        // Production-safe: Get raw data from DB, group in Java (DB-agnostic)
        List<Object[]> rawData = transactionRepository.getMonthlySummaryData(userId, startDate);
        
        Map<String, BigDecimal[]> monthMap = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        
        // Initialize 12 months with zeros
        for (int i = 0; i < 12; i++) {
            LocalDate month = startDate.plusMonths(i);
            monthMap.put(month.format(fmt), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        
        // Process raw data: each row is [LocalDate, TransactionType, BigDecimal]
        for (Object[] row : rawData) {
            LocalDate transactionDate = (LocalDate) row[0];
            TransactionType type = (TransactionType) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            
            String monthKey = transactionDate.format(fmt);
            monthMap.computeIfAbsent(monthKey, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            
            BigDecimal[] totals = monthMap.get(monthKey);
            if (type == TransactionType.INCOME) {
                totals[0] = totals[0].add(amount);
            } else if (type == TransactionType.EXPENSE) {
                totals[1] = totals[1].add(amount);
            }
        }
        
        // Convert to response format
        return monthMap.entrySet().stream()
                .map(e -> new MonthlyData(
                        e.getKey(),
                        e.getValue()[0],
                        e.getValue()[1],
                        e.getValue()[0].subtract(e.getValue()[1])
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategorySpend> getCategoryBreakdown(Long userId, LocalDate start, LocalDate end) {
        List<Transaction> transactions = transactionRepository
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(userId, start, end);
        Map<String, BigDecimal> totalsByCategory = new LinkedHashMap<>();

        for (Transaction transaction : transactions) {
            if (transaction.getType() != TransactionType.EXPENSE) {
                continue;
            }

            String categoryName = transactionClassificationService.resolveCategoryName(transaction);
            totalsByCategory.merge(categoryName, transaction.getAmount(), BigDecimal::add);
        }

        BigDecimal total = totalsByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategorySpend> categoryBreakdown = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : totalsByCategory.entrySet()) {
            BigDecimal amount = entry.getValue();
            double pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
            categoryBreakdown.add(new CategorySpend(
                    entry.getKey(),
                    transactionClassificationService.defaultCategoryColor(entry.getKey(), TransactionType.EXPENSE),
                    amount,
                    pct
            ));
        }

        return categoryBreakdown.stream()
                .sorted(Comparator.comparing(CategorySpend::amount).reversed())
                .toList();
    }

    private List<BudgetAlert> getBudgetAlerts(Long userId) {
        return budgetService.findAll(userId).stream()
                .filter(b -> b.percentUsed() >= b.alertThreshold().doubleValue())
                .map(b -> new BudgetAlert(
                        b.id(), b.name(), b.amount(), b.spent(), b.percentUsed(),
                        b.percentUsed() >= 100 ? "EXCEEDED" : "WARNING"
                ))
                .toList();
    }

    private SpendingStateSummary summarizeSpendingStates(List<Transaction> transactions) {
        long low = 0;
        long normal = 0;
        long high = 0;

        for (Transaction transaction : transactions) {
            if (transaction.getType() != TransactionType.EXPENSE) {
                continue;
            }

            Transaction.SpendingState state = transactionClassificationService.classifySpendingState(transaction.getAmount());

            switch (state) {
                case LOW -> low++;
                case HIGH -> high++;
                default -> normal++;
            }
        }

        return new SpendingStateSummary(low, normal, high);
    }
}
