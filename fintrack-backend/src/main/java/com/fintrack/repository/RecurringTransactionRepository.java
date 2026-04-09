package com.fintrack.repository;

import com.fintrack.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByUserIdAndIsActiveTrueOrderByNextDueDateAsc(Long userId);
    Optional<RecurringTransaction> findByIdAndUserId(Long id, Long userId);

    // For scheduler: find all due today or overdue
    List<RecurringTransaction> findByIsActiveTrueAndAutoCreateTrueAndNextDueDateLessThanEqual(LocalDate date);
}
