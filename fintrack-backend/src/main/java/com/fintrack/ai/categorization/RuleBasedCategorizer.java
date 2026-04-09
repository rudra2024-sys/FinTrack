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
        EXPENSE_RULES.put("zomato", "Food & Dining");
        EXPENSE_RULES.put("swiggy", "Food & Dining");
        EXPENSE_RULES.put("restaurant", "Food & Dining");
        EXPENSE_RULES.put("cafe", "Food & Dining");
        EXPENSE_RULES.put("uber", "Transport");
        EXPENSE_RULES.put("ola", "Transport");
        EXPENSE_RULES.put("metro", "Transport");
        EXPENSE_RULES.put("petrol", "Transport");
        EXPENSE_RULES.put("rent", "Rent");
        EXPENSE_RULES.put("landlord", "Rent");
        EXPENSE_RULES.put("amazon", "Shopping");
        EXPENSE_RULES.put("flipkart", "Shopping");
        EXPENSE_RULES.put("mall", "Shopping");
        EXPENSE_RULES.put("electricity", "Utilities");
        EXPENSE_RULES.put("water bill", "Utilities");
        EXPENSE_RULES.put("internet", "Utilities");
        EXPENSE_RULES.put("netflix", "Subscriptions");
        EXPENSE_RULES.put("spotify", "Subscriptions");
        EXPENSE_RULES.put("hospital", "Healthcare");
        EXPENSE_RULES.put("pharmacy", "Healthcare");
        EXPENSE_RULES.put("movie", "Entertainment");
        EXPENSE_RULES.put("travel", "Travel");
        EXPENSE_RULES.put("airlines", "Travel");

        INCOME_RULES.put("salary", "Salary");
        INCOME_RULES.put("payroll", "Salary");
        INCOME_RULES.put("bonus", "Salary");
        INCOME_RULES.put("freelance", "Freelance");
        INCOME_RULES.put("consulting", "Freelance");
        INCOME_RULES.put("dividend", "Investments");
        INCOME_RULES.put("interest", "Investments");
        INCOME_RULES.put("refund", "Other Income");
    }

    public String categorize(String description, TransactionType type) {
        String normalized = description == null ? "" : description.toLowerCase(Locale.ENGLISH);
        Map<String, String> rules = type == TransactionType.EXPENSE ? EXPENSE_RULES : INCOME_RULES;
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return type == TransactionType.EXPENSE ? "Other" : "Other Income";
    }
}

