package com.fintrack.parser;

import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PdfStatementParser implements StatementParser {

    private static final Pattern DATE_AT_START_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})\\b"
    );
    private static final Pattern DATE_ANYWHERE_PATTERN = Pattern.compile(
            "(\\b\\d{4}-\\d{2}-\\d{2}\\b|\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b|\\b\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4}\\b)"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM))\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([+-]?\\d[\\d,]*\\.\\d{2}|[+-]?\\d[\\d,]*)\\s*(CR|DR|CREDIT|DEBIT)?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GOOGLE_PAY_TRANSACTION_PATTERN = Pattern.compile(
            "(?<date>\\d{1,2}\\s+[A-Za-z]{3},\\s+\\d{4})\\s+"
                    + "(?<time>\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM))\\s+"
                    + "(?<action>Paid|Sent|Received)\\s+"
                    + "(?<amount>\\d[\\d,]*\\.\\d{2})\\s+"
                    + "(?<direction>To|From)\\s+"
                    + "(?<entity>.*?)(?=(?:\\d{1,2}\\s+[A-Za-z]{3},\\s+\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM)\\s+(?:Paid|Sent|Received)\\s+\\d[\\d,]*\\.\\d{2}\\s+(?:To|From)\\s+)|Transaction\\s+Count\\s*:\\s*\\d+|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final DateTimeFormatter GOOGLE_PAY_DATE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM, uuuu")
            .toFormatter(Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mm")
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .appendLiteral(' ')
            .appendPattern("a")
            .toFormatter(Locale.ENGLISH);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("dd/MM/uu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("d-M-uu"),
            DateTimeFormatter.ofPattern("dd-MM-uu"),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd MMM uuuu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd MMMM uuuu").toFormatter(Locale.ENGLISH)
    );

    @Override
    public boolean supports(MultipartFile file) {
        String name = file != null ? file.getOriginalFilename() : null;
        return name != null && name.toLowerCase(Locale.ENGLISH).endsWith(".pdf");
    }

    @Override
    public List<ParsedStatementRow> parse(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String rawText = new PDFTextStripper().getText(document);
            List<ParsedStatementRow> rows = parseText(rawText);
            if (!rows.isEmpty()) {
                return rows;
            }

            String ocrText = extractTextWithOcr(file);
            rows = parseText(ocrText);
            if (!rows.isEmpty()) {
                return rows;
            }

            throw new ApiException(
                    "Could not extract transactions from this PDF automatically, even after OCR. Try a clearer PDF export or upload CSV.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @Override
    public String formatName() {
        return "PDF";
    }

    List<ParsedStatementRow> parseText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        List<ParsedStatementRow> googlePayRows = parseGooglePayTransactions(rawText);
        if (!googlePayRows.isEmpty()) {
            return googlePayRows;
        }

        return parseGenericTransactions(rawText);
    }

    private List<ParsedStatementRow> parseGooglePayTransactions(String rawText) {
        String normalized = normalizeDocument(rawText);
        List<ParsedStatementRow> rows = new ArrayList<>();
        Matcher matcher = GOOGLE_PAY_TRANSACTION_PATTERN.matcher(normalized);

        while (matcher.find()) {
            ParsedStatementRow row = buildGooglePayRow(matcher);
            if (row != null) {
                rows.add(row);
            }
        }

        if (rows.isEmpty()) {
            return List.of();
        }

        int rejectedLines = (int) Arrays.stream(normalized.split("\\R"))
                .map(this::sanitizeLine)
                .filter(line -> !line.isBlank())
                .filter(this::looksLikeTransactionCandidate)
                .filter(line -> !GOOGLE_PAY_TRANSACTION_PATTERN.matcher(line).matches())
                .filter(line -> !isNonTransactionLine(line))
                .count();

        log.info("Parsed {} valid transactions from Google Pay PDF text", rows.size());
        log.info("Rejected {} invalid lines during Google Pay PDF parsing", rejectedLines);
        return rows;
    }

    private ParsedStatementRow buildGooglePayRow(Matcher matcher) {
        try {
            LocalDate transactionDate = LocalDate.parse(matcher.group("date").trim(), GOOGLE_PAY_DATE_FORMAT);
            LocalTime transactionTime = parseTime(matcher.group("time"));
            String action = matcher.group("action").trim();
            BigDecimal amount = parseMoney(matcher.group("amount"));
            String entity = cleanEntity(matcher.group("entity"));

            if (entity.isBlank()) {
                return null;
            }

            TransactionType type = "received".equalsIgnoreCase(action)
                    ? TransactionType.INCOME
                    : TransactionType.EXPENSE;
            String notes = "Parsed from PDF statement: " + action + " " + matcher.group("amount").trim()
                    + " " + matcher.group("direction").trim() + " " + entity;

            return new ParsedStatementRow(
                    transactionDate,
                    transactionTime,
                    entity,
                    amount.abs(),
                    type,
                    entity,
                    notes
            );
        } catch (DateTimeParseException ex) {
            log.debug("Skipping Google Pay row due to date/time parsing failure: {}", matcher.group());
            return null;
        }
    }

    private List<ParsedStatementRow> parseGenericTransactions(String rawText) {
        List<String> logicalLines = combineWrappedLines(rawText);
        List<ParsedStatementRow> rows = new ArrayList<>();
        int rejectedLines = 0;

        for (String line : logicalLines) {
            ParsedStatementRow row = tryParseLine(line);
            if (row != null) {
                rows.add(row);
            } else if (looksLikeTransactionCandidate(line) && !isNonTransactionLine(line)) {
                rejectedLines++;
            }
        }

        if (!rows.isEmpty()) {
            log.info("Parsed {} valid transactions from generic PDF text", rows.size());
            log.info("Rejected {} invalid lines during generic PDF parsing", rejectedLines);
        }

        return rows;
    }

    private String extractTextWithOcr(MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("fintrack-pdf-ocr");
        try {
            Path pdfPath = tempDir.resolve("statement.pdf");
            Files.write(pdfPath, file.getBytes());

            runCommand(tempDir, List.of(
                    "pdftoppm",
                    "-png",
                    "-f", "1",
                    "-l", "5",
                    pdfPath.toString(),
                    tempDir.resolve("page").toString()
            ));

            List<Path> pageImages = Files.list(tempDir)
                    .filter(path -> path.getFileName().toString().startsWith("page-") && path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            if (pageImages.isEmpty()) {
                return "";
            }

            StringBuilder combined = new StringBuilder();
            for (Path image : pageImages) {
                String ocrText = runCommand(tempDir, List.of(
                        "tesseract",
                        image.toString(),
                        "stdout",
                        "-l", "eng",
                        "--psm", "6"
                ));
                combined.append(ocrText).append(System.lineSeparator());
            }
            return combined.toString();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException("PDF OCR processing was interrupted", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("OCR tools are not available for scanned PDFs in this environment", HttpStatus.BAD_REQUEST);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private String runCommand(Path workDir, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new ApiException("OCR command failed: " + command.get(0), HttpStatus.BAD_REQUEST);
        }
        return output;
    }

    private List<String> combineWrappedLines(String rawText) {
        List<String> logicalLines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawLine : rawText.split("\\R")) {
            String line = sanitizeLine(rawLine);
            if (line.isBlank() || isNonTransactionLine(line)) {
                continue;
            }

            if (startsWithDate(line)) {
                if (current.length() > 0) {
                    logicalLines.add(current.toString().trim());
                }
                current.setLength(0);
                current.append(line);
            } else if (current.length() > 0) {
                current.append(' ').append(line);
            }
        }

        if (current.length() > 0) {
            logicalLines.add(current.toString().trim());
        }

        if (logicalLines.isEmpty()) {
            for (String rawLine : rawText.split("\\R")) {
                String line = sanitizeLine(rawLine);
                if (!line.isBlank() && !isNonTransactionLine(line)) {
                    logicalLines.add(line);
                }
            }
        }

        return logicalLines;
    }

    private ParsedStatementRow tryParseLine(String line) {
        Matcher dateMatcher = DATE_ANYWHERE_PATTERN.matcher(line);
        if (!dateMatcher.find()) {
            return null;
        }

        LocalDate date = parseDate(dateMatcher.group(1));
        if (date == null) {
            return null;
        }

        List<AmountToken> amounts = extractAmounts(line);
        if (amounts.isEmpty()) {
            return null;
        }

        AmountToken transactionAmountToken = chooseTransactionAmount(amounts, line);
        if (transactionAmountToken == null) {
            return null;
        }

        BigDecimal amount = parseMoney(transactionAmountToken.value());
        TransactionType type = detectType(line, transactionAmountToken.indicator(), amount);
        LocalTime time = extractTime(line);
        String description = extractDescription(line, dateMatcher.end(), transactionAmountToken.start());
        if (description.isBlank()) {
            return null;
        }

        String merchant = extractMerchant(description);
        return new ParsedStatementRow(
                date,
                time,
                merchant,
                amount.abs(),
                type,
                merchant,
                "Imported from PDF statement"
        );
    }

    private List<AmountToken> extractAmounts(String line) {
        List<AmountToken> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(line);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!looksLikeRealAmount(token)) {
                continue;
            }
            amounts.add(new AmountToken(token, matcher.group(2), matcher.start()));
        }
        return amounts;
    }

    private AmountToken chooseTransactionAmount(List<AmountToken> amounts, String line) {
        if (amounts.isEmpty()) {
            return null;
        }

        String normalized = line.toLowerCase(Locale.ENGLISH);
        for (AmountToken token : amounts) {
            String indicator = token.indicator() == null ? "" : token.indicator().toLowerCase(Locale.ENGLISH);
            if (indicator.equals("dr") || indicator.equals("cr") || indicator.equals("debit") || indicator.equals("credit")) {
                return token;
            }
        }

        if (amounts.size() >= 2) {
            return amounts.get(amounts.size() - 2);
        }
        if (normalized.contains("deposit") || normalized.contains("withdrawal")) {
            return amounts.get(0);
        }
        return amounts.get(0);
    }

    private String extractDescription(String line, int dateEnd, int amountStart) {
        int start = Math.max(dateEnd, 0);
        int end = amountStart > start ? amountStart : line.length();
        String description = line.substring(start, end)
                .replaceAll("\\s{2,}", " ")
                .trim();

        description = description.replaceAll("^(value\\s+date|txn\\s+date|transaction\\s+date)\\s*", "");
        description = description.replaceAll("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b", " ");
        description = description.replaceAll("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM)\\b", " ");
        return description.replaceAll("\\s{2,}", " ").trim();
    }

    private boolean startsWithDate(String line) {
        return DATE_AT_START_PATTERN.matcher(line).find();
    }

    private String normalizeDocument(String raw) {
        return raw == null ? "" : raw
                .replace('\u00A0', ' ')
                .replace("â‚¹", "")
                .replace("₹", "")
                .replace("INR", "")
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" +", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    private String sanitizeLine(String raw) {
        return normalizeDocument(raw).trim();
    }

    private boolean isNonTransactionLine(String line) {
        String normalized = line.toLowerCase(Locale.ENGLISH);
        return normalized.startsWith("google pay statement")
                || normalized.matches("^[a-z]+\\s+\\d{4}$")
                || normalized.startsWith("transaction count");
    }

    private boolean looksLikeTransactionCandidate(String line) {
        String normalized = line.toLowerCase(Locale.ENGLISH);
        return DATE_ANYWHERE_PATTERN.matcher(line).find()
                || normalized.contains("paid")
                || normalized.contains("sent")
                || normalized.contains("received");
    }

    private String cleanEntity(String rawEntity) {
        return sanitizeLine(rawEntity)
                .replaceAll("(?i)transaction\\s+count\\s*:\\s*\\d+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private boolean looksLikeRealAmount(String token) {
        String plain = token.replace(",", "").trim();
        if (plain.length() <= 1) {
            return false;
        }
        return plain.matches("[+-]?\\d+(\\.\\d{1,2})?");
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim().toUpperCase(Locale.ENGLISH), TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalTime extractTime(String line) {
        Matcher matcher = TIME_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        return parseTime(matcher.group(1));
    }

    private BigDecimal parseMoney(String raw) {
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            throw new ApiException("Unsupported amount value in PDF statement: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    private TransactionType detectType(String line, String indicator, BigDecimal amount) {
        String normalized = line.toLowerCase(Locale.ENGLISH);
        String marker = indicator == null ? "" : indicator.toLowerCase(Locale.ENGLISH);

        if (amount.signum() < 0) {
            return TransactionType.EXPENSE;
        }
        if (marker.equals("dr") || marker.equals("debit")) {
            return TransactionType.EXPENSE;
        }
        if (marker.equals("cr") || marker.equals("credit")) {
            return TransactionType.INCOME;
        }
        if (normalized.contains("salary") || normalized.contains("interest") || normalized.contains("refund") || normalized.contains("cashback")) {
            return TransactionType.INCOME;
        }
        if (normalized.contains("deposit") || normalized.contains("credit")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private String extractMerchant(String description) {
        String merchant = description.replaceAll("\\s{2,}", " ").trim();
        if (merchant.length() > 120) {
            merchant = merchant.substring(0, 120);
        }
        return merchant;
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private record AmountToken(String value, String indicator, int start) {}
}
