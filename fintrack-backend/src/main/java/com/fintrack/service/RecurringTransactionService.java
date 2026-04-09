package com.fintrack.service;

import com.fintrack.dto.recurring.RecurringDTOs.*;
import com.fintrack.entity.*;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringRepo;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public Response create(Long userId, CreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        Account account = accountRepository.findByIdAndUserId(req.accountId(), userId)
                .orElseThrow(() -> new ApiException("Account not found", HttpStatus.NOT_FOUND));
        Category category = req.categoryId() != null
                ? categoryRepository.findById(req.categoryId()).orElse(null) : null;

        RecurringTransaction rt = RecurringTransaction.builder()
                .user(user)
                .account(account)
                .category(category)
                .type(req.type())
                .amount(req.amount())
                .description(req.description())
                .frequency(req.frequency())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .nextDueDate(req.startDate())
                .autoCreate(req.autoCreate() != null ? req.autoCreate() : true)
                .build();

        return toResponse(recurringRepo.save(rt));
    }

    @Transactional(readOnly = true)
    public List<Response> findAll(Long userId) {
        return recurringRepo.findByUserIdAndIsActiveTrueOrderByNextDueDateAsc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public Response update(Long id, Long userId, UpdateRequest req) {
        RecurringTransaction rt = getRecurring(id, userId);
        if (req.amount() != null) rt.setAmount(req.amount());
        if (req.description() != null) rt.setDescription(req.description());
        if (req.frequency() != null) rt.setFrequency(req.frequency());
        if (req.endDate() != null) rt.setEndDate(req.endDate());
        if (req.autoCreate() != null) rt.setAutoCreate(req.autoCreate());
        if (req.isActive() != null) rt.setIsActive(req.isActive());
        return toResponse(recurringRepo.save(rt));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        recurringRepo.delete(getRecurring(id, userId));
    }

    /**
     * Runs daily at midnight — auto-creates transactions for due recurring entries.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void processDueRecurringTransactions() {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> due = recurringRepo
                .findByIsActiveTrueAndAutoCreateTrueAndNextDueDateLessThanEqual(today);

        log.info("Processing {} due recurring transactions", due.size());

        for (RecurringTransaction rt : due) {
            try {
                // Skip if past end date
                if (rt.getEndDate() != null && today.isAfter(rt.getEndDate())) {
                    rt.setIsActive(false);
                    recurringRepo.save(rt);
                    continue;
                }

                // Create the transaction
                Transaction tx = Transaction.builder()
                        .user(rt.getUser())
                        .account(rt.getAccount())
                        .category(rt.getCategory())
                        .type(rt.getType())
                        .amount(rt.getAmount())
                        .description(rt.getDescription())
                        .transactionDate(rt.getNextDueDate())
                        .isRecurring(true)
                        .recurringId(rt.getId())
                        .build();
                transactionRepository.save(tx);

                // Update account balance
                Account account = rt.getAccount();
                if (rt.getType() == Transaction.TransactionType.INCOME) {
                    account.setBalance(account.getBalance().add(rt.getAmount()));
                } else {
                    account.setBalance(account.getBalance().subtract(rt.getAmount()));
                }
                accountRepository.save(account);

                // Advance next due date
                rt.setLastProcessedDate(rt.getNextDueDate());
                rt.setNextDueDate(rt.getFrequency().nextDate(rt.getNextDueDate()));
                recurringRepo.save(rt);

            } catch (Exception e) {
                log.error("Failed to process recurring transaction id={}: {}", rt.getId(), e.getMessage());
            }
        }
    }

    private RecurringTransaction getRecurring(Long id, Long userId) {
        return recurringRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Recurring transaction not found", HttpStatus.NOT_FOUND));
    }

    private Response toResponse(RecurringTransaction rt) {
        return new Response(
                rt.getId(),
                rt.getAccount().getId(),
                rt.getAccount().getName(),
                rt.getCategory() != null ? rt.getCategory().getId() : null,
                rt.getCategory() != null ? rt.getCategory().getName() : null,
                rt.getType(),
                rt.getAmount(),
                rt.getDescription(),
                rt.getFrequency(),
                rt.getStartDate(),
                rt.getEndDate(),
                rt.getNextDueDate(),
                rt.getLastProcessedDate(),
                rt.getIsActive(),
                rt.getAutoCreate()
        );
    }
}
