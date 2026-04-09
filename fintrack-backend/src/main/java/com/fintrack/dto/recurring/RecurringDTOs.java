package com.fintrack.dto.recurring;

import com.fintrack.entity.RecurringTransaction.Frequency;
import com.fintrack.entity.Transaction.TransactionType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class RecurringDTOs {

    public record CreateRequest(
            @NotNull Long accountId,
            Long categoryId,
            @NotNull TransactionType type,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @NotNull Frequency frequency,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean autoCreate
    ) {}

    public record UpdateRequest(
            @Positive BigDecimal amount,
            @Size(max = 255) String description,
            Frequency frequency,
            LocalDate endDate,
            Boolean autoCreate,
            Boolean isActive
    ) {}

    public record Response(
            Long id,
            Long accountId,
            String accountName,
            Long categoryId,
            String categoryName,
            TransactionType type,
            BigDecimal amount,
            String description,
            Frequency frequency,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextDueDate,
            LocalDate lastProcessedDate,
            Boolean isActive,
            Boolean autoCreate
    ) {}
}
