package com.fintrack.parser;

import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.exception.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CsvStatementParser implements StatementParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)
    );

    @Override
    public boolean supports(MultipartFile file) {
        String name = file != null ? file.getOriginalFilename() : null;
        return name != null && name.toLowerCase(Locale.ENGLISH).endsWith(".csv");
    }

    @Override
    public List<ParsedStatementRow> parse(MultipartFile file) throws IOException {
        return parse(file.getInputStream());
    }

    @Override
    public String formatName() {
        return "CSV";
    }

    public List<ParsedStatementRow> parse(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<ParsedStatementRow> rows = new ArrayList<>();
            Map<String, Integer> headers = parser.getHeaderMap();
            if (headers == null || headers.isEmpty()) {
                throw new ApiException("CSV header row is required", HttpStatus.BAD_REQUEST);
            }

            for (CSVRecord record : parser) {
                if (record == null || record.size() == 0) {
                    continue;
                }

                String description = value(record, headers, Set.of("description", "narration", "details", "transaction details", "remarks"));
                String dateValue = value(record, headers, Set.of("date", "transaction date", "transaction_date", "txn date", "posted date"));
                String amountValue = value(record, headers, Set.of("amount", "txn amount", "transaction amount"));
                String debitValue = value(record, headers, Set.of("debit", "withdrawal", "debits"));
                String creditValue = value(record, headers, Set.of("credit", "deposit", "credits"));
                String typeValue = value(record, headers, Set.of("type", "transaction type", "dr/cr"));
                String merchant = value(record, headers, Set.of("merchant", "payee"));
                String notes = value(record, headers, Set.of("notes", "note", "comment"));

                if ((description == null || description.isBlank()) && (amountValue == null || amountValue.isBlank())
                        && (debitValue == null || debitValue.isBlank()) && (creditValue == null || creditValue.isBlank())) {
                    continue;
                }

                LocalDate transactionDate = parseDate(dateValue);
                ParsedAmount parsedAmount = parseAmount(amountValue, debitValue, creditValue, typeValue);

                rows.add(new ParsedStatementRow(
                        transactionDate,
                        description == null || description.isBlank() ? "Imported Transaction" : description.trim(),
                        parsedAmount.amount(),
                        parsedAmount.type(),
                        merchant,
                        notes
                ));
            }

            if (rows.isEmpty()) {
                throw new ApiException("No valid transactions were found in the uploaded CSV", HttpStatus.BAD_REQUEST);
            }
            return rows;
        } catch (IOException ex) {
            throw new ApiException("Unable to read uploaded CSV file", HttpStatus.BAD_REQUEST);
        }
    }

    private String value(CSVRecord record, Map<String, Integer> headers, Set<String> aliases) {
        for (String header : headers.keySet()) {
            if (aliases.contains(normalize(header))) {
                String value = record.get(header);
                return value != null ? value.trim() : null;
            }
        }
        return null;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException("Each statement row must include a transaction date", HttpStatus.BAD_REQUEST);
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new ApiException("Unsupported date format in statement: " + raw, HttpStatus.BAD_REQUEST);
    }

    private ParsedAmount parseAmount(String amount, String debit, String credit, String type) {
        if (credit != null && !credit.isBlank()) {
            return new ParsedAmount(parseMoney(credit), TransactionType.INCOME);
        }
        if (debit != null && !debit.isBlank()) {
            return new ParsedAmount(parseMoney(debit), TransactionType.EXPENSE);
        }

        BigDecimal parsed = parseMoney(amount);
        if (parsed.signum() < 0) {
            return new ParsedAmount(parsed.abs(), TransactionType.EXPENSE);
        }

        String normalizedType = normalize(type);
        if (Set.of("expense", "debit", "dr").contains(normalizedType)) {
            return new ParsedAmount(parsed.abs(), TransactionType.EXPENSE);
        }
        if (Set.of("income", "credit", "cr", "deposit").contains(normalizedType)) {
            return new ParsedAmount(parsed.abs(), TransactionType.INCOME);
        }
        return new ParsedAmount(parsed.abs(), TransactionType.INCOME);
    }

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException("Each statement row must include an amount", HttpStatus.BAD_REQUEST);
        }
        String normalized = raw.replace(",", "")
                .replace("₹", "")
                .replace("INR", "")
                .replace("CR", "")
                .replace("DR", "")
                .trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new ApiException("Unsupported amount value in statement: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ENGLISH).replace('_', ' ');
    }

    private record ParsedAmount(BigDecimal amount, TransactionType type) {}
}
