package com.fintrack.service;

import com.fintrack.dto.budget.BudgetDTOs.*;
import com.fintrack.entity.*;
import com.fintrack.entity.Budget.BudgetPeriod;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Response create(Long userId, CreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        Category category = req.categoryId() != null
                ? categoryRepository.findById(req.categoryId()).orElse(null) : null;

        Budget budget = Budget.builder()
                .user(user)
                .category(category)
                .name(req.name())
                .amount(req.amount())
                .period(req.period())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .alertThreshold(req.alertThreshold() != null ? req.alertThreshold() : new BigDecimal("80.00"))
                .build();

        return toResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<Response> findAll(Long userId) {
        return budgetRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Response findById(Long id, Long userId) {
        Budget budget = getBudget(id, userId);
        return toResponse(budget);
    }

    @Transactional
    public Response update(Long id, Long userId, UpdateRequest req) {
        Budget budget = getBudget(id, userId);
        if (req.name() != null) budget.setName(req.name());
        if (req.amount() != null) budget.setAmount(req.amount());
        if (req.period() != null) budget.setPeriod(req.period());
        if (req.endDate() != null) budget.setEndDate(req.endDate());
        if (req.alertThreshold() != null) budget.setAlertThreshold(req.alertThreshold());
        if (req.isActive() != null) budget.setIsActive(req.isActive());
        return toResponse(budgetRepository.save(budget));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Budget budget = getBudget(id, userId);
        budgetRepository.delete(budget);
    }

    // ---- Helpers ----

    private Budget getBudget(Long id, Long userId) {
        return budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Budget not found", HttpStatus.NOT_FOUND));
    }

    private Response toResponse(Budget budget) {
        LocalDate[] range = getPeriodRange(budget);
        BigDecimal spent = BigDecimal.ZERO;

        if (budget.getCategory() != null) {
            spent = transactionRepository.sumExpensesByCategoryAndDateRange(
                    budget.getUser().getId(), budget.getCategory().getId(), range[0], range[1]);
        }

        BigDecimal remaining = budget.getAmount().subtract(spent).max(BigDecimal.ZERO);
        double percentUsed = budget.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(budget.getAmount(), 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

        boolean alertTriggered = BigDecimal.valueOf(percentUsed)
                .compareTo(budget.getAlertThreshold()) >= 0;

        return new Response(
                budget.getId(),
                budget.getCategory() != null ? budget.getCategory().getId() : null,
                budget.getCategory() != null ? budget.getCategory().getName() : null,
                budget.getCategory() != null ? budget.getCategory().getColor() : null,
                budget.getName(),
                budget.getAmount(),
                spent,
                remaining,
                percentUsed,
                budget.getPeriod(),
                budget.getStartDate(),
                budget.getEndDate(),
                budget.getAlertThreshold(),
                budget.getIsActive(),
                alertTriggered
        );
    }

    private LocalDate[] getPeriodRange(Budget budget) {
        LocalDate now = LocalDate.now();
        LocalDate start = budget.getStartDate();
        LocalDate end;

        end = switch (budget.getPeriod()) {
            case WEEKLY    -> start.plusWeeks(1).minusDays(1);
            case MONTHLY   -> start.plusMonths(1).minusDays(1);
            case QUARTERLY -> start.plusMonths(3).minusDays(1);
            case YEARLY    -> start.plusYears(1).minusDays(1);
        };

        if (budget.getEndDate() != null && budget.getEndDate().isBefore(end)) {
            end = budget.getEndDate();
        }

        return new LocalDate[]{start, end.isAfter(now) ? now : end};
    }
}
