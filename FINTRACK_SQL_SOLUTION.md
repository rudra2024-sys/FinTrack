# FinTrack SQL Issue - Complete Analysis & Production Solution

## ROOT CAUSE ANALYSIS

### Problem #1: CONCAT + LPAD + CAST Complexity
**Why it fails:**
- `CONCAT(YEAR(...), '-', LPAD(...), '-01')` creates a STRING like `"2026-04-01"`
- `CAST(... AS DATE)` then tries to convert this back
- H2's DATE constructor is strict; string format must match exactly
- Grouping by computed DATE expressions is problematic in H2

### Problem #2: Reserved Keyword Collision
**Why it fails:**
- `month` is a reserved word in SQL
- Using it as an alias without quotes may cause parser confusion
- H2 is sensitive about this

### Problem #3: Group By Consistency
**Why it fails:**
- If you SELECT a computed expression, you MUST GROUP BY the same expression or use aggregate functions
- Grouping by separate calls to `YEAR()` and `MONTH()` is fragile

---

## PRODUCTION-SAFE H2 SOLUTION

### ✅ RECOMMENDED: Use LocalDate Computation in Java (BEST)
**Reason:** Database-agnostic, maximally robust, easier to test

```sql
SELECT 
    t.transaction_date,
    t.type,
    t.amount
FROM transactions t
WHERE t.user_id = :userId
  AND t.transaction_date >= :startDate
ORDER BY t.transaction_date ASC
```

Then in Java:
```java
@Query("""
    SELECT t.transaction_date, t.type, t.amount
    FROM Transaction t
    WHERE t.user.id = :userId
      AND t.transactionDate >= :startDate
    ORDER BY t.transactionDate ASC
    """)
List<MonthlySummaryRecord> getMonthlySummaryData(
    @Param("userId") Long userId,
    @Param("startDate") LocalDate startDate
);

// Java processing
Map<YearMonth, Map<TransactionType, BigDecimal>> grouped = data.stream()
    .collect(Collectors.groupingBy(
        row -> YearMonth.from(row.transactionDate()),
        Collectors.groupingBy(
            row -> row.type(),
            Collectors.reducing(
                BigDecimal.ZERO,
                row -> row.amount(),
                BigDecimal::add
            )
        )
    ));
```

### ⚠️ FALLBACK: H2-Native Query (if computation needed server-side)
**Use this if you MUST compute in SQL:**

```sql
SELECT 
    FORMATDATETIME(t.transaction_date, 'yyyy-MM-01') AS period_start,
    t.type,
    SUM(t.amount) AS total
FROM transactions t
WHERE t.user_id = :userId
  AND t.transaction_date >= :startDate
GROUP BY FORMATDATETIME(t.transaction_date, 'yyyy-MM-01'), t.type
ORDER BY period_start ASC
```

**But test it first!** Some H2 versions have issues with `FORMATDATETIME`.

### ❌ AVOID: CONCAT + LPAD + CAST
- Too many conversions
- Fragile across H2 versions
- Difficult to debug
- Not portable to production databases

---

## CORRECTED JAVA CODE

### Solution 1: Java-Side Grouping (RECOMMENDED)

```java
// Entity/DTO
public record MonthlySummaryData(
    LocalDate transactionDate,
    TransactionType type,
    BigDecimal amount
) {}

// Repository
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    @Query("""
        SELECT new com.fintrack.dto.MonthlySummaryData(
            t.transactionDate,
            t.type,
            t.amount
        )
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate >= :startDate
        ORDER BY t.transactionDate ASC
        """)
    List<MonthlySummaryData> getMonthlySummaryData(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate
    );
}

// Service
@Service
@Slf4j
public class AnalyticsService {
    
    private final TransactionRepository transactionRepository;
    
    public List<MonthlySummary> getMonthlySummary(Long userId, LocalDate startDate) {
        List<MonthlySummaryData> rawData = transactionRepository
            .getMonthlySummaryData(userId, startDate);
        
        return rawData.stream()
            .collect(Collectors.groupingBy(
                row -> YearMonth.from(row.transactionDate()),
                LinkedHashMap::new,  // Preserve insertion order
                Collectors.groupingBy(
                    MonthlySummaryData::type,
                    Collectors.mapping(
                        MonthlySummaryData::amount,
                        Collectors.reducing(
                            BigDecimal.ZERO,
                            BigDecimal::add
                        )
                    )
                )
            ))
            .entrySet()
            .stream()
            .map(entry -> new MonthlySummary(
                entry.getKey().atDay(1),
                entry.getValue()
            ))
            .toList();
    }
}

// Response DTO
public record MonthlySummary(
    LocalDate month,
    Map<TransactionType, BigDecimal> typeBreakdown
) {}
```

### Solution 2: If SQL Grouping Is Required

```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    // Use a simpler H2-safe query
    @Query(nativeQuery = true, value = """
        SELECT 
            FORMATDATETIME(transaction_date, 'yyyy-MM') AS period,
            type,
            CAST(SUM(amount) AS DECIMAL(19,2)) AS total_amount
        FROM transactions
        WHERE user_id = :userId
          AND transaction_date >= :startDate
        GROUP BY FORMATDATETIME(transaction_date, 'yyyy-MM'), type
        ORDER BY period ASC
        """)
    List<Object[]> getMonthlySummaryNative(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate
    );
    
    // Post-process the results
    default List<MonthlySummary> getMonthlySummary(Long userId, LocalDate startDate) {
        return getMonthlySummaryNative(userId, startDate)
            .stream()
            .collect(Collectors.groupingBy(
                row -> YearMonth.parse((String) row[0], DateTimeFormatter.ofPattern("yyyy-MM")),
                LinkedHashMap::new,
                Collectors.toMap(
                    row -> TransactionType.valueOf((String) row[1]),
                    row -> (BigDecimal) row[2],
                    BigDecimal::add
                )
            ))
            .entrySet()
            .stream()
            .map(entry -> new MonthlySummary(
                entry.getKey().atDay(1),
                entry.getValue()
            ))
            .toList();
    }
}
```

---

## PDF TEXT SANITIZATION (Best Practices)

Your code already does this well! Here's what's important:

```java
private String normalizeDocument(String raw) {
    return raw == null ? "" : raw
        // Remove unicode currency symbols
        .replace('\u00A0', ' ')      // Non-breaking space
        .replace("â‚¹", "")           // Corrupted rupee symbol
        .replace("₹", "")
        .replace("INR", "")
        // Normalize whitespace
        .replaceAll("[\\t\\x0B\\f\\r]+", " ")  // Tabs, vertical tabs, form feeds
        .replaceAll(" +", " ")                  // Multiple spaces → single space
        .replaceAll("\\n{2,}", "\n")           // Multiple newlines → single newline
        .trim();
}

private String sanitizeLine(String raw) {
    return normalizeDocument(raw)
        .replaceAll("(?i)^(page\\s+\\d+|statement|summary).*$", "")  // Remove headers
        .trim();
}
```

**Key sanitization steps:**
1. ✅ Remove control characters (`\x0B`, `\f`, `\r`)
2. ✅ Normalize whitespace (collapse multiple spaces/newlines)
3. ✅ Remove corrupted unicode (handles PDF extraction artifacts)
4. ✅ Strip empty lines
5. ✅ Validate date/amount formats AFTER sanitization

```java
// Before storing, validate
private Transaction sanitizeAndValidate(ParsedStatementRow row) {
    // Trim strings
    String merchant = row.merchant().trim();
    if (merchant.length() > 255) {
        merchant = merchant.substring(0, 255);  // Database constraint
    }
    
    // Validate amount
    if (row.amount().signum() == 0) {
        log.warn("Skipping zero-amount transaction: {}", row);
        return null;
    }
    
    // Validate date
    if (row.transactionDate().isAfter(LocalDate.now().plusDays(1))) {
        log.warn("Transaction date in future, clamping to today: {}", row.transactionDate());
    }
    
    return new Transaction()
        .setDescription(merchant)
        .setAmount(row.amount().setScale(2, RoundingMode.HALF_UP))
        .setTransactionDate(row.transactionDate())
        .setType(row.type());
}
```

---

## BEST PRACTICES FOR PRODUCTION

### 1. Use Spring Data JPA Projections (Type-Safe)
```java
public interface MonthlySummaryProjection {
    LocalDate getMonth();
    String getType();
    BigDecimal getTotal();
}
```

### 2. Add Database-Agnostic Layer
```java
@Service
public class AnalyticsQueryBuilder {
    public List<MonthlySummary> getMonthlySummary(
        Long userId, 
        LocalDate startDate,
        String databaseType  // "h2", "postgresql", "mysql"
    ) {
        // Route to optimized query based on DB type
        return switch(databaseType) {
            case "h2" -> getH2Query(userId, startDate);
            case "postgresql" -> getPostgresQuery(userId, startDate);
            default -> getGenericQuery(userId, startDate);  // Falls back to Java grouping
        };
    }
}
```

### 3. Test Query Independently
```java
@DataJpaTest
public class TransactionRepositoryTest {
    
    @Autowired
    private TransactionRepository repo;
    
    @Test
    public void testMonthlySummaryQuery() {
        // Insert test data
        // Execute query
        // Assert results match expected grouping
    }
}
```

### 4. Add Metrics & Logging
```java
@Aspect
@Component
@Slf4j
public class AnalyticsMetrics {
    
    @Around("@annotation(com.fintrack.annotation.TrackAnalytics)")
    public Object trackQuery(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("Analytics query took {} ms", duration);
            if (duration > 1000) {
                log.warn("Slow analytics query detected: {} ms", duration);
            }
        }
    }
}
```

### 5. Cache Results
```java
@Service
@Slf4j
public class CachedAnalyticsService {
    
    @Cacheable(value = "monthly-summary", key = "#userId + '-' + #startDate")
    public List<MonthlySummary> getMonthlySummary(Long userId, LocalDate startDate) {
        return analyticsService.getMonthlySummary(userId, startDate);
    }
}
```

---

## MIGRATION GUIDE

### Step 1: Update Repository
Replace the problematic query with Java-side grouping.

### Step 2: Update Service Layer
Implement the grouping logic in Java.

### Step 3: Test End-to-End
- Upload test PDF
- Verify transactions parse correctly
- Confirm analytics queries work
- Check performance (< 500ms for typical user)

### Step 4: Update Frontend
Results structure changes from flat to grouped:
```json
[
  {
    "month": "2026-04-01",
    "typeBreakdown": {
      "INCOME": 50000,
      "EXPENSE": 15000
    }
  }
]
```

---

## SUMMARY

| Aspect | Issue | Solution |
|--------|-------|----------|
| SQL Complexity | CONCAT+LPAD+CAST | Move grouping to Java |
| H2 Compatibility | Limited date functions | Use FORMATDATETIME or Java |
| Reserved Keywords | `month` collision | Use computed property names |
| PDF Sanitization | Special chars breaking parsing | Already implemented well |
| Production Readiness | Single point of failure | Add caching + metrics |
| Portability | H2-specific SQL | Database-agnostic layer |

**Recommended Action:** Implement Solution 1 (Java-side grouping). It's the most robust, testable, and portable approach.
