package com.fintrack.service;

import com.fintrack.dto.transaction.TransactionDTOs.*;
import com.fintrack.entity.*;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.exception.ApiException;
import com.fintrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionClassificationService transactionClassificationService;

    @Transactional
    public Response create(Long userId, CreateRequest req) {
        User user = getUser(userId);
        Account account = getAccount(req.accountId(), userId);
        Category category = req.categoryId() != null
                ? categoryRepository.findById(req.categoryId()).orElse(null) : null;

        Transaction tx = Transaction.builder()
                .user(user)
                .account(account)
                .category(category)
                .type(req.type())
                .amount(req.amount().abs())
                .description(req.description())
                .merchant(req.merchant())
                .notes(req.notes())
                .transactionDate(req.transactionDate())
                .transactionTime(req.transactionTime())
                .spendingState(resolveSpendingState(req.amount()))
                .tags(req.tags())
                .build();

        tx = transactionRepository.save(tx);
        updateAccountBalance(account, req.type(), req.amount());

        return toResponse(tx);
    }

    @Transactional(readOnly = true)
    public PagedResponse findAll(Long userId, FilterRequest filter) {
        Pageable pageable = PageRequest.of(filter.page(), filter.size());
        boolean hasFilters = filter.type() != null
                || filter.categoryId() != null
                || filter.accountId() != null
                || filter.startDate() != null
                || filter.endDate() != null
                || (filter.search() != null && !filter.search().isBlank());

        Page<Transaction> page = hasFilters
                ? transactionRepository.findWithFilters(
                        userId,
                        filter.type(),
                        filter.categoryId(),
                        filter.accountId(),
                        filter.startDate(),
                        filter.endDate(),
                        filter.search(),
                        pageable
                )
                : transactionRepository.findByUserIdOrderByTransactionDateDesc(userId, pageable);

        List<Response> content = page.getContent().stream().map(this::toResponse).toList();
        return new PagedResponse(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public Response findById(Long id, Long userId) {
        return toResponse(getTransaction(id, userId));
    }

    @Transactional
    public Response update(Long id, Long userId, UpdateRequest req) {
        Transaction tx = getTransaction(id, userId);

        // Reverse old balance effect
        reverseAccountBalance(tx.getAccount(), tx.getType(), tx.getAmount());

        if (req.accountId() != null) tx.setAccount(getAccount(req.accountId(), userId));
        if (req.categoryId() != null) tx.setCategory(categoryRepository.findById(req.categoryId()).orElse(null));
        if (req.type() != null) tx.setType(req.type());
        if (req.amount() != null) tx.setAmount(req.amount().abs());
        if (req.description() != null) tx.setDescription(req.description());
        if (req.merchant() != null) tx.setMerchant(req.merchant());
        if (req.notes() != null) tx.setNotes(req.notes());
        if (req.transactionDate() != null) tx.setTransactionDate(req.transactionDate());
        if (req.transactionTime() != null) tx.setTransactionTime(req.transactionTime());
        if (req.tags() != null) tx.setTags(req.tags());
        tx.setSpendingState(resolveSpendingState(tx.getAmount()));

        // Apply new balance effect
        updateAccountBalance(tx.getAccount(), tx.getType(), tx.getAmount());

        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Transaction tx = getTransaction(id, userId);
        reverseAccountBalance(tx.getAccount(), tx.getType(), tx.getAmount());
        transactionRepository.delete(tx);
    }

    // ---- Helpers ----

    private void updateAccountBalance(Account account, TransactionType type, BigDecimal amount) {
        if (type == TransactionType.INCOME) {
            account.setBalance(account.getBalance().add(amount));
        } else if (type == TransactionType.EXPENSE) {
            account.setBalance(account.getBalance().subtract(amount));
        }
        accountRepository.save(account);
    }

    private void reverseAccountBalance(Account account, TransactionType type, BigDecimal amount) {
        if (type == TransactionType.INCOME) {
            account.setBalance(account.getBalance().subtract(amount));
        } else if (type == TransactionType.EXPENSE) {
            account.setBalance(account.getBalance().add(amount));
        }
        accountRepository.save(account);
    }

    private Transaction getTransaction(Long id, Long userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Transaction not found", HttpStatus.NOT_FOUND));
    }

    private Account getAccount(Long accountId, Long userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ApiException("Account not found", HttpStatus.NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
    }

    public Response toResponse(Transaction tx) {
        Transaction.SpendingState spendingState = transactionClassificationService.classifySpendingState(tx.getAmount());
        String categoryName = transactionClassificationService.resolveCategoryName(tx);
        String categoryColor = transactionClassificationService.resolveCategoryColor(tx);
        String categoryIcon = transactionClassificationService.resolveCategoryIcon(tx);
        return new Response(
                tx.getId(),
                tx.getAccount().getId(),
                tx.getAccount().getName(),
                tx.getCategory() != null ? tx.getCategory().getId() : null,
                categoryName,
                categoryColor,
                categoryIcon,
                tx.getType(),
                tx.getAmount(),
                tx.getDescription(),
                tx.getMerchant(),
                tx.getNotes(),
                tx.getTransactionDate(),
                tx.getTransactionTime() != null ? tx.getTransactionTime().format(TIME_FORMATTER) : null,
                tx.getMerchant() != null ? tx.getMerchant() : tx.getDescription(),
                spendingState,
                spendingState,
                tx.getIsRecurring(),
                tx.getTags(),
                tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null
        );
    }

    private Transaction.SpendingState resolveSpendingState(BigDecimal amount) {
        return transactionClassificationService.classifySpendingStateWithLog(amount);
    }
}
