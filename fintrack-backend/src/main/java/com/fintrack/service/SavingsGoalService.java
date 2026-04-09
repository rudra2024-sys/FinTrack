package com.fintrack.service;

import com.fintrack.dto.savings.SavingsDTOs.*;
import com.fintrack.entity.*;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    public GoalResponse create(Long userId, CreateGoalRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        Account account = req.accountId() != null
                ? accountRepository.findByIdAndUserId(req.accountId(), userId).orElse(null) : null;

        SavingsGoal goal = SavingsGoal.builder()
                .user(user)
                .account(account)
                .name(req.name())
                .description(req.description())
                .targetAmount(req.targetAmount())
                .monthlyContribution(req.monthlyContribution())
                .targetDate(req.targetDate())
                .icon(req.icon())
                .color(req.color())
                .build();

        return toResponse(savingsGoalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> findAll(Long userId) {
        return savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse findById(Long id, Long userId) {
        return toResponse(getGoal(id, userId));
    }

    @Transactional
    public GoalResponse update(Long id, Long userId, UpdateGoalRequest req) {
        SavingsGoal goal = getGoal(id, userId);
        if (req.name() != null) goal.setName(req.name());
        if (req.description() != null) goal.setDescription(req.description());
        if (req.targetAmount() != null) goal.setTargetAmount(req.targetAmount());
        if (req.monthlyContribution() != null) goal.setMonthlyContribution(req.monthlyContribution());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        if (req.status() != null) goal.setStatus(req.status());
        if (req.icon() != null) goal.setIcon(req.icon());
        if (req.color() != null) goal.setColor(req.color());
        return toResponse(savingsGoalRepository.save(goal));
    }

    @Transactional
    public GoalResponse contribute(Long id, Long userId, ContributeRequest req) {
        SavingsGoal goal = getGoal(id, userId);
        BigDecimal newAmount = goal.getCurrentAmount().add(req.amount());
        goal.setCurrentAmount(newAmount);

        SavingsContribution contribution = SavingsContribution.builder()
                .goal(goal)
                .amount(req.amount())
                .notes(req.notes())
                .build();
        goal.getContributions().add(contribution);

        // Auto-complete if target reached
        if (newAmount.compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(SavingsGoal.GoalStatus.COMPLETED);
        }

        return toResponse(savingsGoalRepository.save(goal));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        savingsGoalRepository.delete(getGoal(id, userId));
    }

    // ---- Helpers ----

    private SavingsGoal getGoal(Long id, Long userId) {
        return savingsGoalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Savings goal not found", HttpStatus.NOT_FOUND));
    }

    private GoalResponse toResponse(SavingsGoal goal) {
        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount()).max(BigDecimal.ZERO);
        double percentComplete = goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? goal.getCurrentAmount()
                    .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100 : 0;

        Integer monthsToGoal = null;
        if (goal.getMonthlyContribution() != null
                && goal.getMonthlyContribution().compareTo(BigDecimal.ZERO) > 0
                && remaining.compareTo(BigDecimal.ZERO) > 0) {
            monthsToGoal = remaining
                    .divide(goal.getMonthlyContribution(), 0, RoundingMode.CEILING)
                    .intValue();
        } else if (goal.getTargetDate() != null) {
            monthsToGoal = (int) ChronoUnit.MONTHS.between(LocalDate.now(), goal.getTargetDate());
        }

        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getDescription(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                remaining,
                percentComplete,
                goal.getMonthlyContribution(),
                goal.getTargetDate(),
                monthsToGoal,
                goal.getStatus(),
                goal.getIcon(),
                goal.getColor(),
                goal.getCreatedAt() != null ? goal.getCreatedAt().toString() : null
        );
    }
}
