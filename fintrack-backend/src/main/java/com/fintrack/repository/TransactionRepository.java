package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:accountId IS NULL OR t.account.id = :accountId)
          AND (:startDate IS NULL OR t.transactionDate >= :startDate)
          AND (:endDate IS NULL OR t.transactionDate <= :endDate)
          AND (:search IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    Page<Transaction> findWithFilters(
            @Param("userId") Long userId,
            @Param("type") Transaction.TransactionType type,
            @Param("categoryId") Long categoryId,
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = :type
          AND t.transactionDate BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumByUserAndTypeAndDateRange(
            @Param("userId") Long userId,
            @Param("type") Transaction.TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.category.id = :categoryId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumExpensesByCategoryAndDateRange(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Monthly summary for charts
    @Query(value = """
        SELECT
            DATE_TRUNC('month', transaction_date) AS month,
            type,
            SUM(amount) AS total
        FROM transactions
        WHERE user_id = :userId
          AND transaction_date >= :startDate
        GROUP BY DATE_TRUNC('month', transaction_date), type
        ORDER BY month
        """, nativeQuery = true)
    List<Object[]> getMonthlySummary(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate
    );

    // Category breakdown
    @Query("""
        SELECT t.category.name, t.category.color, SUM(t.amount) as total
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :startDate AND :endDate
          AND t.category IS NOT NULL
        GROUP BY t.category.name, t.category.color
        ORDER BY total DESC
        """)
    List<Object[]> getCategoryBreakdown(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    boolean existsByUserIdAndImportHash(Long userId, String importHash);

    List<Transaction> findTop10ByUserIdAndTypeOrderByTransactionDateDescCreatedAtDesc(
            Long userId, Transaction.TransactionType type
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumExpensesByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = 'INCOME'
          AND t.transactionDate BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumIncomeByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT MAX(t.transactionDate)
        FROM Transaction t
        WHERE t.user.id = :userId
        """)
    LocalDate findLatestTransactionDateByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COALESCE(t.merchant, t.description), COUNT(t), SUM(t.amount)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :startDate AND :endDate
        GROUP BY COALESCE(t.merchant, t.description)
        ORDER BY SUM(t.amount) DESC
        """)
    List<Object[]> getTopExpenseMerchants(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    List<Transaction> findByUserIdOrderByTransactionDateAscCreatedAtAsc(Long userId);

    List<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(
            Long userId, LocalDate startDate, LocalDate endDate
    );
}
