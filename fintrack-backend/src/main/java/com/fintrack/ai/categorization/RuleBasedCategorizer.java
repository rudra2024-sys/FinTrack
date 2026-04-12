package com.fintrack.ai.categorization;

import com.fintrack.entity.Transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class RuleBasedCategorizer {

    private static final Map<String, String> EXPENSE_RULES = new LinkedHashMap<>();
    private static final Map<String, String> INCOME_RULES = new LinkedHashMap<>();

    static {
        EXPENSE_RULES.put("zomato", "Food");
        EXPENSE_RULES.put("swiggy", "Food");
        EXPENSE_RULES.put("restaurant", "Food");
        EXPENSE_RULES.put("snacks", "Food");
        EXPENSE_RULES.put("food", "Food");
        EXPENSE_RULES.put("coffee", "Food");
        EXPENSE_RULES.put("grocery", "Food");

        EXPENSE_RULES.put("uber", "Transport");
        EXPENSE_RULES.put("ola", "Transport");
        EXPENSE_RULES.put("taxi", "Transport");
        EXPENSE_RULES.put("cab", "Transport");

        EXPENSE_RULES.put("electricity", "Bills");
        EXPENSE_RULES.put("recharge", "Bills");
        EXPENSE_RULES.put("internet", "Bills");
        EXPENSE_RULES.put("rent", "Bills");
        EXPENSE_RULES.put("insurance", "Bills");
        EXPENSE_RULES.put("bill", "Bills");

        EXPENSE_RULES.put("amazon", "Shopping");
        EXPENSE_RULES.put("flipkart", "Shopping");
        EXPENSE_RULES.put("electronics", "Shopping");
        EXPENSE_RULES.put("shopping", "Shopping");

        EXPENSE_RULES.put("pharmacy", "Health");
        EXPENSE_RULES.put("hospital", "Health");
        EXPENSE_RULES.put("medical", "Health");

        EXPENSE_RULES.put("netflix", "Entertainment");
        EXPENSE_RULES.put("movie", "Entertainment");
        EXPENSE_RULES.put("gaming", "Entertainment");
        EXPENSE_RULES.put("ott", "Entertainment");

        EXPENSE_RULES.put("salon", "Personal");
        EXPENSE_RULES.put("gym", "Personal");

        INCOME_RULES.put("salary", "Salary");
        INCOME_RULES.put("payroll", "Salary");
        INCOME_RULES.put("freelance", "Freelance");
        INCOME_RULES.put("bonus", "Bonus");
        INCOME_RULES.put("refund", "Refund");
        INCOME_RULES.put("cashback", "Refund");
    }

    public String categorize(String description, TransactionType type) {
        return categorize(description, null, null, type);
    }

    public String categorize(String description, String merchant, String notes, TransactionType type) {
        String normalized = normalize(description, merchant, notes);
        Map<String, String> rules = type == TransactionType.EXPENSE ? EXPENSE_RULES : INCOME_RULES;
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return type == TransactionType.EXPENSE ? "Others" : "Others";
    }

    private String normalize(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(value.trim().toLowerCase(Locale.ENGLISH));
        }
        return builder.toString();
    }
}
