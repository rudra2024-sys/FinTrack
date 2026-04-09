package com.fintrack.goals;

import com.fintrack.dto.goals.FinancialGoalDTOs.*;
import com.fintrack.entity.FinancialGoal;
import com.fintrack.entity.User;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.FinancialGoalRepository;
import com.fintrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialGoalService {

    private final FinancialGoalRepository financialGoalRepository;
    private final UserRepository userRepository;

    @Transactional
    public Response upsert(Long userId, UpsertRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        FinancialGoal goal = financialGoalRepository.findByUserId(userId)
                .orElse(FinancialGoal.builder().user(user).build());

        goal.setRent(request.rent());
        goal.setSavingsGoal(request.savingsGoal());
        goal.setMonthlyBudget(request.monthlyBudget());
        goal.setNotes(request.notes());

        return toResponse(financialGoalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public Response get(Long userId) {
        FinancialGoal goal = financialGoalRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException("Financial goal profile not found", HttpStatus.NOT_FOUND));
        return toResponse(goal);
    }

    @Transactional(readOnly = true)
    public FinancialGoal findOptional(Long userId) {
        return financialGoalRepository.findByUserId(userId).orElse(null);
    }

    private Response toResponse(FinancialGoal goal) {
        return new Response(
                goal.getId(),
                goal.getRent(),
                goal.getSavingsGoal(),
                goal.getMonthlyBudget(),
                goal.getNotes(),
                goal.getUpdatedAt()
        );
    }
}

