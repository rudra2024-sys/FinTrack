package com.fintrack.repository;

import com.fintrack.entity.FinancialGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {
    Optional<FinancialGoal> findByUserId(Long userId);
}

