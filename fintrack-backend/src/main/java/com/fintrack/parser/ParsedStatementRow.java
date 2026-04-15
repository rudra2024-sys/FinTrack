package com.fintrack.parser;

import com.fintrack.entity.Transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record ParsedStatementRow(
        LocalDate transactionDate,
        LocalTime transactionTime,
        String description,
        BigDecimal amount,
        TransactionType type,
        String merchant,
        String notes
) {}
