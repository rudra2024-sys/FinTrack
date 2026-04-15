package com.fintrack.intelligence;

import com.fintrack.ai.AiMlGateway;
import com.fintrack.dto.intelligence.IntelligenceDTOs.*;
import com.fintrack.entity.Transaction;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class FinancialIntelligenceService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TransactionRepository transactionRepository;
    private final AiMlGateway aiMlGateway;

    @Transactional(readOnly = true)
    public IntelligenceResponse analyzeUserTransactions(Long userId, AnalyzeRequest request) {
        List<Transaction> transactions = request.startDate() != null && request.endDate() != null
                ? transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(
                        userId, request.startDate(), request.endDate()
                )
                : transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId);

        if (transactions.isEmpty()) {
            throw new ApiException("No transactions found for AI intelligence analysis", HttpStatus.BAD_REQUEST);
        }

        List<MlTransaction> payload = transactions.stream()
                .map(this::toMlTransaction)
                .toList();

        return aiMlGateway.analyzeFinancialIntelligence(new MlAnalyzeRequest(
                payload,
                request.incomeStability(),
                request.savingsRate(),
                request.debtPressure()
        ));
    }

    public PdfIntelligenceResponse analyzePdf(
            MultipartFile file,
            Double incomeStability,
            Double savingsRate,
            Double debtPressure
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("PDF statement file is required", HttpStatus.BAD_REQUEST);
        }
        return aiMlGateway.analyzePdfStatement(file, incomeStability, savingsRate, debtPressure);
    }

    private MlTransaction toMlTransaction(Transaction transaction) {
        return new MlTransaction(
                transaction.getTransactionDate(),
                transaction.getTransactionTime() != null ? transaction.getTransactionTime().format(TIME_FORMATTER) : null,
                transaction.getType() == Transaction.TransactionType.INCOME ? "credit" : "debit",
                transaction.getMerchant() != null && !transaction.getMerchant().isBlank()
                        ? transaction.getMerchant()
                        : transaction.getDescription(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getAiCategoryLabel(),
                null
        );
    }
}
