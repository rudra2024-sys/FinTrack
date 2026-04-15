package com.fintrack.dto.transaction;

import com.fintrack.entity.Transaction.SpendingState;
import com.fintrack.entity.Transaction.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class TransactionDTOs {

    public record CreateRequest(
            @NotNull Long accountId,
            Long categoryId,
            @NotNull TransactionType type,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @Size(max = 100) String merchant,
            String notes,
            @NotNull LocalDate transactionDate,
            LocalTime transactionTime,
            String tags
    ) {}

    public record UpdateRequest(
            Long accountId,
            Long categoryId,
            TransactionType type,
            @Positive BigDecimal amount,
            @Size(max = 255) String description,
            @Size(max = 100) String merchant,
            String notes,
            LocalDate transactionDate,
            LocalTime transactionTime,
            String tags
    ) {}

    public record Response(
            Long id,
            Long accountId,
            String accountName,
            Long categoryId,
            String categoryName,
            String categoryColor,
            String categoryIcon,
            TransactionType type,
            BigDecimal amount,
            String description,
            String merchant,
            String notes,
            LocalDate transactionDate,
            String transactionTime,
            String entity,
            SpendingState state,
            SpendingState hmmState,
            Boolean isRecurring,
            String tags,
            String createdAt
    ) {}

    public record FilterRequest(
            TransactionType type,
            Long categoryId,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            int page,
            int size
    ) {}

    public record PagedResponse(
            java.util.List<Response> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last
    ) {}
}
