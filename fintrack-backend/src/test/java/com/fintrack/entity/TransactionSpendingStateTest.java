package com.fintrack.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionSpendingStateTest {

    @Test
    void classifiesSpendingStateByThreshold() {
        assertThat(Transaction.SpendingState.fromAmount(new BigDecimal("499.99")))
                .isEqualTo(Transaction.SpendingState.LOW);
        assertThat(Transaction.SpendingState.fromAmount(new BigDecimal("500.00")))
                .isEqualTo(Transaction.SpendingState.NORMAL);
        assertThat(Transaction.SpendingState.fromAmount(new BigDecimal("2000.00")))
                .isEqualTo(Transaction.SpendingState.NORMAL);
        assertThat(Transaction.SpendingState.fromAmount(new BigDecimal("2000.01")))
                .isEqualTo(Transaction.SpendingState.HIGH);
    }
}
