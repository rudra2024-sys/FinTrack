package com.fintrack.dto.statement;

import com.fintrack.entity.Statement.StatementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class StatementDTOs {

    public record UploadResponse(
            Long id,
            String fileName,
            String source,
            LocalDate statementDate,
            StatementStatus status,
            Integer importedCount,
            Integer skippedDuplicates,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            Instant uploadDate,
            String notes
    ) {}

    public record SummaryResponse(
            Long id,
            Long accountId,
            String accountName,
            String fileName,
            String source,
            LocalDate statementDate,
            StatementStatus status,
            Integer transactionCount,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            Instant uploadDate,
            String notes
    ) {}

    public record ListResponse(List<SummaryResponse> statements) {}
}

