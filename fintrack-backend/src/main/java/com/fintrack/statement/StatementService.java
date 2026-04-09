package com.fintrack.statement;

import com.fintrack.ai.AiMlGateway;
import com.fintrack.dto.intelligence.IntelligenceDTOs;
import com.fintrack.dto.statement.StatementDTOs.*;
import com.fintrack.entity.*;
import com.fintrack.exception.ApiException;
import com.fintrack.parser.ParsedStatementRow;
import com.fintrack.parser.StatementParser;
import com.fintrack.parser.StatementParserRegistry;
import com.fintrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StatementService {
    private static final int VARCHAR_255_SAFE_LIMIT = 240;

    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final StatementParserRegistry statementParserRegistry;
    private final AiMlGateway aiMlGateway;

    @Transactional
    public UploadResponse upload(
            Long userId,
            Long accountId,
            MultipartFile file,
            String source,
            LocalDate statementDate,
            boolean applyToAccountBalance
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Statement file is required", HttpStatus.BAD_REQUEST);
        }
        String originalFileName = file.getOriginalFilename();
        StatementParser statementParser = statementParserRegistry.resolve(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ApiException("Account not found", HttpStatus.NOT_FOUND));

        Statement statement = Statement.builder()
                .user(user)
                .account(account)
                .fileName(trimToNullable(originalFileName, VARCHAR_255_SAFE_LIMIT))
                .source(trimToNullable(
                        source != null && !source.isBlank() ? source : statementParser.formatName() + " Upload",
                        VARCHAR_255_SAFE_LIMIT
                ))
                .statementDate(statementDate)
                .status(Statement.StatementStatus.PROCESSING)
                .notes(trimToNullable(
                        statementParser.formatName() + " statement received for AI-assisted processing",
                        VARCHAR_255_SAFE_LIMIT
                ))
                .build();
        statement = statementRepository.save(statement);

        try {
            List<ParsedStatementRow> parsedRows = parseRows(file, statementParser);
            List<Category> categories = categoryRepository.findAllForUser(userId);
            List<Transaction> importedTransactions = new ArrayList<>();
            int duplicateCount = 0;
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;

            for (ParsedStatementRow row : parsedRows) {
                String importHash = buildImportHash(accountId, row);
                if (transactionRepository.existsByUserIdAndImportHash(userId, importHash)) {
                    duplicateCount++;
                    continue;
                }

                String aiCategoryLabel = aiMlGateway.categorize(row.description(), row.type());
                Category category = resolveCategory(categories, aiCategoryLabel, row.type());

                Transaction transaction = Transaction.builder()
                        .user(user)
                        .account(account)
                        .statement(statement)
                        .category(category)
                        .type(row.type())
                        .amount(row.amount())
                        .description(trimToNullable(row.description(), VARCHAR_255_SAFE_LIMIT))
                        .merchant(trimToNullable(row.merchant(), VARCHAR_255_SAFE_LIMIT))
                        .notes(trimToNullable(row.notes(), VARCHAR_255_SAFE_LIMIT))
                        .transactionDate(row.transactionDate())
                        .isRecurring(false)
                        .importHash(importHash)
                        .aiCategoryLabel(trimToNullable(aiCategoryLabel, VARCHAR_255_SAFE_LIMIT))
                        .importSource(trimToNullable(statement.getSource(), VARCHAR_255_SAFE_LIMIT))
                        .build();
                importedTransactions.add(transaction);

                if (row.type() == Transaction.TransactionType.INCOME) {
                    totalIncome = totalIncome.add(row.amount());
                } else if (row.type() == Transaction.TransactionType.EXPENSE) {
                    totalExpenses = totalExpenses.add(row.amount());
                }
            }

            transactionRepository.saveAll(importedTransactions);

            if (applyToAccountBalance) {
                account.setBalance(account.getBalance().add(totalIncome.subtract(totalExpenses)));
                accountRepository.save(account);
            }

            statement.setStatus(Statement.StatementStatus.PROCESSED);
            statement.setTransactionCount(importedTransactions.size());
            statement.setTotalIncome(totalIncome);
            statement.setTotalExpenses(totalExpenses);
            statement.setNotes(trimToNullable(
                    duplicateCount > 0
                            ? "Processed with " + duplicateCount + " duplicate transaction(s) skipped"
                            : "Processed successfully",
                    VARCHAR_255_SAFE_LIMIT
            ));
            statementRepository.save(statement);

            return new UploadResponse(
                    statement.getId(),
                    statement.getFileName(),
                    statement.getSource(),
                    statement.getStatementDate(),
                    statement.getStatus(),
                    importedTransactions.size(),
                    duplicateCount,
                    totalIncome,
                    totalExpenses,
                    statement.getUploadDate(),
                    statement.getNotes()
            );
        } catch (IOException ex) {
            statement.setStatus(Statement.StatementStatus.FAILED);
            statement.setNotes(trimToNullable("Unable to read uploaded statement", VARCHAR_255_SAFE_LIMIT));
            statementRepository.save(statement);
            throw new ApiException("Unable to read uploaded statement", HttpStatus.BAD_REQUEST);
        } catch (RuntimeException ex) {
            statement.setStatus(Statement.StatementStatus.FAILED);
            statement.setNotes(trimToNullable(ex.getMessage(), VARCHAR_255_SAFE_LIMIT));
            statementRepository.save(statement);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ListResponse listStatements(Long userId) {
        List<SummaryResponse> summaries = statementRepository.findByUserIdOrderByUploadDateDesc(userId)
                .stream()
                .map(statement -> new SummaryResponse(
                        statement.getId(),
                        statement.getAccount().getId(),
                        statement.getAccount().getName(),
                        statement.getFileName(),
                        statement.getSource(),
                        statement.getStatementDate(),
                        statement.getStatus(),
                        statement.getTransactionCount(),
                        statement.getTotalIncome(),
                        statement.getTotalExpenses(),
                        statement.getUploadDate(),
                        statement.getNotes()
                ))
                .toList();
        return new ListResponse(summaries);
    }

    private Category resolveCategory(List<Category> categories, String label, Transaction.TransactionType type) {
        if (label != null) {
            for (Category category : categories) {
                if (category.getName().equalsIgnoreCase(label)) {
                    return category;
                }
            }
        }
        String fallback = type == Transaction.TransactionType.EXPENSE ? "Other" : "Other Income";
        return categories.stream()
                .filter(category -> category.getName().equalsIgnoreCase(fallback))
                .findFirst()
                .orElse(null);
    }

    private List<ParsedStatementRow> parseRows(MultipartFile file, StatementParser statementParser) throws IOException {
        if (isPdf(file)) {
            try {
                IntelligenceDTOs.PdfExtractionResponse extraction = aiMlGateway.extractPdfStatement(file);
                List<ParsedStatementRow> mlRows = toParsedRows(extraction);
                if (!mlRows.isEmpty()) {
                    return mlRows;
                }
            } catch (RuntimeException ignored) {
                // Use the local parser when ML extraction is unavailable or yields no usable rows.
            }
        }
        return statementParser.parse(file);
    }

    private List<ParsedStatementRow> toParsedRows(IntelligenceDTOs.PdfExtractionResponse extraction) {
        if (extraction == null || extraction.transactions() == null) {
            return List.of();
        }

        List<ParsedStatementRow> rows = new ArrayList<>();
        for (IntelligenceDTOs.MlTransaction transaction : extraction.transactions()) {
            if (transaction == null || transaction.date() == null || transaction.amount() == null) {
                continue;
            }

            String typeValue = transaction.transaction_type() == null
                    ? ""
                    : transaction.transaction_type().trim().toLowerCase(Locale.ENGLISH);
            Transaction.TransactionType type = "credit".equals(typeValue)
                    ? Transaction.TransactionType.INCOME
                    : Transaction.TransactionType.EXPENSE;

            String description = firstNonBlank(transaction.description(), transaction.merchant_person(), "Imported PDF Transaction");
            String merchant = firstNonBlank(transaction.merchant_person(), description);

            rows.add(new ParsedStatementRow(
                    transaction.date(),
                    description,
                    transaction.amount().abs(),
                    type,
                    merchant,
                    "Imported from ML PDF extraction"
            ));
        }
        return rows;
    }

    private String buildImportHash(Long accountId, ParsedStatementRow row) {
        String fingerprint = accountId + "|" + row.transactionDate() + "|" + row.description() + "|" + row.amount() + "|" + row.type();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 should always be available", ex);
        }
    }

    private String trimToNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private boolean isPdf(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        return originalFileName != null && originalFileName.toLowerCase(Locale.ENGLISH).endsWith(".pdf");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }
}
