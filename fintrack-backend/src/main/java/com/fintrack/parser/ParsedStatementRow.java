package com.fintrack.parser;

import com.fintrack.entity.Transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedStatementRow(
        LocalDate transactionDate,
        String description,
        BigDecimal amount,
        TransactionType type,
        String merchant,
        String notes
) {}

