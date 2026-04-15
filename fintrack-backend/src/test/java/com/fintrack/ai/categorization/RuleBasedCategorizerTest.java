package com.fintrack.ai.categorization;

import com.fintrack.entity.Transaction.TransactionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedCategorizerTest {

    private final RuleBasedCategorizer categorizer = new RuleBasedCategorizer();

    @Test
    void categorizesExpenseKeywordsUsingRequiredBuckets() {
        assertThat(categorizer.categorize("Swiggy food order", TransactionType.EXPENSE)).isEqualTo("Food");
        assertThat(categorizer.categorize("Uber ride", TransactionType.EXPENSE)).isEqualTo("Transport");
        assertThat(categorizer.categorize("Electricity bill payment", TransactionType.EXPENSE)).isEqualTo("Bills");
        assertThat(categorizer.categorize("Monthly rent transfer", TransactionType.EXPENSE)).isEqualTo("Bills");
        assertThat(categorizer.categorize("Amazon shopping", TransactionType.EXPENSE)).isEqualTo("Shopping");
        assertThat(categorizer.categorize("Hospital visit", TransactionType.EXPENSE)).isEqualTo("Health");
        assertThat(categorizer.categorize("Netflix subscription", TransactionType.EXPENSE)).isEqualTo("Entertainment");
        assertThat(categorizer.categorize("Gaming wallet top-up", TransactionType.EXPENSE)).isEqualTo("Entertainment");
        assertThat(categorizer.categorize("Salon appointment", TransactionType.EXPENSE)).isEqualTo("Personal");
        assertThat(categorizer.categorize("Charity transfer", TransactionType.EXPENSE)).isEqualTo("Others");
    }

    @Test
    void categorizesUsingMerchantAndNotesContext() {
        assertThat(categorizer.categorize("Paid via UPI", "Snacks Shop", null, TransactionType.EXPENSE))
                .isEqualTo("Food");
        assertThat(categorizer.categorize("Transfer", "Local Gym", "membership", TransactionType.EXPENSE))
                .isEqualTo("Personal");
    }

    @Test
    void categorizesCommonIncomeSourcesDeterministically() {
        assertThat(categorizer.categorize("Monthly salary credit", TransactionType.INCOME)).isEqualTo("Salary");
        assertThat(categorizer.categorize("Freelance project payment", TransactionType.INCOME)).isEqualTo("Freelance");
        assertThat(categorizer.categorize("Annual bonus", TransactionType.INCOME)).isEqualTo("Bonus");
        assertThat(categorizer.categorize("Refund processed", TransactionType.INCOME)).isEqualTo("Refund");
        assertThat(categorizer.categorize("Random incoming transfer", TransactionType.INCOME)).isEqualTo("Others");
    }
}
