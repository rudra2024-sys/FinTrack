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
import com.fintrack.service.TransactionClassificationService;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatementService {
    private static final int VARCHAR_255_SAFE_LIMIT = 240;
    private static final DateTimeFormatter ML_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mm")
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .appendLiteral(' ')
            .appendPattern("a")
            .toFormatter(Locale.ENGLISH);

    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final StatementParserRegistry statementParserRegistry;
    private final AiMlGateway aiMlGateway;
    private final TransactionClassificationService transactionClassificationService;

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
            if (parsedRows.isEmpty()) {
                throw new ApiException("No valid transactions could be extracted from the uploaded statement", HttpStatus.BAD_REQUEST);
            }
            Map<String, Category> categoriesByName = new LinkedHashMap<>();
            categoryRepository.findAllForUser(userId)
                    .forEach(category -> categoriesByName.put(category.getName().toLowerCase(Locale.ENGLISH), category));
            List<Transaction> importedTransactions = new ArrayList<>();
            int duplicateCount = 0;
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            Set<String> batchImportHashes = new HashSet<>();

            for (ParsedStatementRow row : parsedRows) {
                String importHash = buildImportHash(accountId, row);
                if (!batchImportHashes.add(importHash) || transactionRepository.existsByUserIdAndImportHash(userId, importHash)) {
                    duplicateCount++;
                    continue;
                }

                String categoryLabel = transactionClassificationService.classifyCategory(
                        row.type(),
                        row.description(),
                        row.merchant(),
                        row.notes()
                );
                Category category = resolveCategory(categoriesByName, user, categoryLabel, row.type());

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
                        .transactionTime(row.transactionTime())
                        .spendingState(transactionClassificationService.classifySpendingStateWithLog(row.amount()))
                        .isRecurring(false)
                        .importHash(importHash)
                        .aiCategoryLabel(trimToNullable(categoryLabel, VARCHAR_255_SAFE_LIMIT))
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
            log.info("Imported {} unique transactions for statement {}", importedTransactions.size(), statement.getId());
            if (duplicateCount > 0) {
                log.info("Skipped {} duplicate transactions for statement {}", duplicateCount, statement.getId());
            }

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
                            ? "Processed successfully with " + duplicateCount + " duplicate transaction(s) skipped"
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

    private Category resolveCategory(
            Map<String, Category> categoriesByName,
            User user,
            String label,
            Transaction.TransactionType type
    ) {
        String resolvedLabel = firstNonBlank(label, type == Transaction.TransactionType.EXPENSE ? "Others" : "Others");
        String key = resolvedLabel.toLowerCase(Locale.ENGLISH);
        Category existing = categoriesByName.get(key);
        if (existing != null) {
            return existing;
        }

        Category created = categoryRepository.save(Category.builder()
                .user(user)
                .name(resolvedLabel)
                .type(type == Transaction.TransactionType.INCOME ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE)
                .color(transactionClassificationService.defaultCategoryColor(resolvedLabel, type))
                .icon(transactionClassificationService.defaultCategoryIcon(resolvedLabel, type))
                .isSystem(false)
                .build());
        categoriesByName.put(key, created);
        return created;
    }

    private List<ParsedStatementRow> parseRows(MultipartFile file, StatementParser statementParser) throws IOException {
        List<ParsedStatementRow> localRows = List.of();
        try {
            localRows = statementParser.parse(file);
            if (!localRows.isEmpty()) {
                return localRows;
            }
        } catch (RuntimeException ex) {
            log.warn("Local statement parser returned no usable rows, trying ML fallback: {}", ex.getMessage());
        }

        if (isPdf(file)) {
            try {
                IntelligenceDTOs.PdfExtractionResponse extraction = aiMlGateway.extractPdfStatement(file);
                List<ParsedStatementRow> mlRows = toParsedRows(extraction);
                if (!mlRows.isEmpty()) {
                    return mlRows;
                }
            } catch (RuntimeException ignored) {
                log.warn("ML PDF extraction unavailable, using local parser fallback: {}", ignored.getMessage());
            }
        }
        return localRows;
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
                    parseMlTime(transaction.time()),
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
        String fingerprint = accountId
                + "|" + row.transactionDate()
                + "|" + row.transactionTime()
                + "|" + normalizeFingerprintText(row.description())
                + "|" + row.amount()
                + "|" + row.type();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 should always be available", ex);
        }
    }

    private LocalTime parseMlTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim().toUpperCase(Locale.ENGLISH), ML_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
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

    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ENGLISH);
    }

}
