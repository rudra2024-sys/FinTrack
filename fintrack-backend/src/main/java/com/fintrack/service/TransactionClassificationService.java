package com.fintrack.service;

import com.fintrack.ai.categorization.RuleBasedCategorizer;
import com.fintrack.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionClassificationService {

    private final RuleBasedCategorizer ruleBasedCategorizer;

    public Transaction.SpendingState classifySpendingState(BigDecimal amount) {
        BigDecimal normalizedAmount = amount == null ? null : amount.abs();
        return Transaction.SpendingState.fromAmount(normalizedAmount);
    }

    public Transaction.SpendingState classifySpendingStateWithLog(BigDecimal amount) {
        Transaction.SpendingState state = classifySpendingState(amount);
        BigDecimal normalizedAmount = amount == null ? null : amount.abs();
        log.info("Amount: {} -> State: {}", formatAmountForLog(normalizedAmount), state);
        return state;
    }

    public String classifyCategory(Transaction.TransactionType type, String description, String merchant, String notes) {
        return ruleBasedCategorizer.categorize(description, merchant, notes, type);
    }

    public String resolveCategoryName(Transaction transaction) {
        if (transaction == null) {
            return "Others";
        }

        String storedCategory = transaction.getCategory() != null ? transaction.getCategory().getName() : null;
        if (storedCategory != null && !storedCategory.isBlank() && !"others".equalsIgnoreCase(storedCategory)) {
            return storedCategory;
        }

        return classifyCategory(
                transaction.getType(),
                transaction.getDescription(),
                transaction.getMerchant(),
                transaction.getNotes()
        );
    }

    public String resolveCategoryColor(Transaction transaction) {
        String storedCategory = transaction != null && transaction.getCategory() != null
                ? transaction.getCategory().getName()
                : null;
        String storedColor = transaction != null && transaction.getCategory() != null
                ? transaction.getCategory().getColor()
                : null;
        if (storedColor != null && !storedColor.isBlank() && !"others".equalsIgnoreCase(storedCategory)) {
            return storedColor;
        }
        return defaultCategoryColor(resolveCategoryName(transaction), transaction != null ? transaction.getType() : null);
    }

    public String resolveCategoryIcon(Transaction transaction) {
        String storedCategory = transaction != null && transaction.getCategory() != null
                ? transaction.getCategory().getName()
                : null;
        String storedIcon = transaction != null && transaction.getCategory() != null
                ? transaction.getCategory().getIcon()
                : null;
        if (storedIcon != null && !storedIcon.isBlank() && !"others".equalsIgnoreCase(storedCategory)) {
            return storedIcon;
        }
        return defaultCategoryIcon(resolveCategoryName(transaction), transaction != null ? transaction.getType() : null);
    }

    public String defaultCategoryColor(String label, Transaction.TransactionType type) {
        String normalized = label == null ? "" : label.toLowerCase(Locale.ENGLISH);
        if (type == Transaction.TransactionType.INCOME) {
            return "#22c55e";
        }
        return switch (normalized) {
            case "food" -> "#f97316";
            case "transport" -> "#8b5cf6";
            case "bills" -> "#f59e0b";
            case "shopping" -> "#ec4899";
            case "health" -> "#ef4444";
            case "entertainment" -> "#6366f1";
            case "personal" -> "#06b6d4";
            default -> "#94a3b8";
        };
    }

    public String defaultCategoryIcon(String label, Transaction.TransactionType type) {
        String normalized = label == null ? "" : label.toLowerCase(Locale.ENGLISH);
        if (type == Transaction.TransactionType.INCOME) {
            return "plus-circle";
        }
        return switch (normalized) {
            case "food" -> "utensils";
            case "transport" -> "car";
            case "bills" -> "zap";
            case "shopping" -> "shopping-bag";
            case "health" -> "heart";
            case "entertainment" -> "tv";
            case "personal" -> "user";
            default -> "more-horizontal";
        };
    }

    private String formatAmountForLog(BigDecimal amount) {
        return amount == null ? "null" : amount.stripTrailingZeros().toPlainString();
    }
}
