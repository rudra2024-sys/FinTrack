package com.fintrack.config;

import com.fintrack.entity.Account;
import com.fintrack.entity.Category;
import com.fintrack.entity.RecurringTransaction;
import com.fintrack.entity.SavingsGoal;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.RecurringTransactionRepository;
import com.fintrack.repository.SavingsGoalRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class DemoDataConfig {

    public static final String DEMO_EMAIL = "test@fintrack.com";
    public static final String DEMO_PASSWORD = "password123";

    @Bean
    public org.springframework.boot.CommandLineRunner seedDemoUser(
            UserRepository userRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            SavingsGoalRepository savingsGoalRepository,
            RecurringTransactionRepository recurringTransactionRepository,
            TransactionRepository transactionRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            User demoUser = userRepository.findByEmail(DEMO_EMAIL)
                    .orElseGet(() -> User.builder()
                            .email(DEMO_EMAIL)
                            .fullName("Test User")
                            .currency("INR")
                            .role("ROLE_USER")
                            .isActive(true)
                            .build());

            demoUser.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
            demoUser.setFullName("Test User");
            demoUser.setCurrency("INR");
            demoUser.setRole("ROLE_USER");
            demoUser.setIsActive(true);
            demoUser = userRepository.save(demoUser);

            List<Account> accounts = accountRepository.findByUserIdAndIsActiveTrue(demoUser.getId());
            if (accounts.isEmpty()) {
                accountRepository.save(Account.builder()
                        .user(demoUser)
                        .name("Demo Checking")
                        .type(Account.AccountType.CHECKING)
                        .balance(new BigDecimal("25000.00"))
                        .currency("INR")
                        .institution("FinTrack Demo Bank")
                        .color("#c8f000")
                        .isActive(true)
                        .build());
                log.info("Seeded default account for demo user {}", DEMO_EMAIL);
                accounts = accountRepository.findByUserIdAndIsActiveTrue(demoUser.getId());
            }

            if (transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(demoUser.getId()).isEmpty() && !accounts.isEmpty()) {
                seedDemoTransactions(demoUser, accounts.get(0), categoryRepository, transactionRepository);
            }
            if (savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(demoUser.getId()).isEmpty() && !accounts.isEmpty()) {
                seedDemoGoals(demoUser, accounts.get(0), savingsGoalRepository);
            }
            if (recurringTransactionRepository.findByUserIdAndIsActiveTrueOrderByNextDueDateAsc(demoUser.getId()).isEmpty() && !accounts.isEmpty()) {
                seedRecurringTransactions(demoUser, accounts.get(0), categoryRepository, recurringTransactionRepository);
            }

            log.info("Demo user ready: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
        };
    }

    private void seedDemoTransactions(
            User demoUser,
            Account account,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository
    ) {
        Map<String, Category> categoriesByName = new HashMap<>();
        categoryRepository.findAllForUser(demoUser.getId()).forEach(category -> categoriesByName.put(category.getName(), category));

        Category salary = categoriesByName.computeIfAbsent("Salary", ignored -> categoryRepository.save(Category.builder()
                .user(demoUser)
                .name("Salary")
                .type(Category.CategoryType.INCOME)
                .color("#c8f000")
                .icon("briefcase")
                .isSystem(false)
                .build()));

        Category food = categoriesByName.computeIfAbsent("Food", ignored -> categoryRepository.save(Category.builder()
                .user(demoUser)
                .name("Food")
                .type(Category.CategoryType.EXPENSE)
                .color("#ff8a65")
                .icon("utensils")
                .isSystem(false)
                .build()));

        Category rent = categoriesByName.computeIfAbsent("Rent", ignored -> categoryRepository.save(Category.builder()
                .user(demoUser)
                .name("Rent")
                .type(Category.CategoryType.EXPENSE)
                .color("#00e5ff")
                .icon("home")
                .isSystem(false)
                .build()));

        Category travel = categoriesByName.computeIfAbsent("Travel", ignored -> categoryRepository.save(Category.builder()
                .user(demoUser)
                .name("Travel")
                .type(Category.CategoryType.EXPENSE)
                .color("#ffd166")
                .icon("car")
                .isSystem(false)
                .build()));

        Category shopping = categoriesByName.computeIfAbsent("Shopping", ignored -> categoryRepository.save(Category.builder()
                .user(demoUser)
                .name("Shopping")
                .type(Category.CategoryType.EXPENSE)
                .color("#e8e4dc")
                .icon("bag")
                .isSystem(false)
                .build()));

        Category utilities = categoriesByName.computeIfAbsent("Utilities", ignored -> categoryRepository.save(Category.builder()
                .user(demoUser)
                .name("Utilities")
                .type(Category.CategoryType.EXPENSE)
                .color("#7bd389")
                .icon("bolt")
                .isSystem(false)
                .build()));

        LocalDate today = LocalDate.now();
        List<Transaction> demoTransactions = List.of(
                buildTransaction(demoUser, account, salary, Transaction.TransactionType.INCOME, "85000.00", "Monthly Salary", "FinTrack Payroll", today.minusDays(35), false, "demo-salary-1"),
                buildTransaction(demoUser, account, rent, Transaction.TransactionType.EXPENSE, "22000.00", "Apartment Rent", "Sunrise Residency", today.minusDays(33), true, "demo-rent-1"),
                buildTransaction(demoUser, account, food, Transaction.TransactionType.EXPENSE, "1850.00", "Groceries", "Big Basket", today.minusDays(28), false, "demo-food-1"),
                buildTransaction(demoUser, account, utilities, Transaction.TransactionType.EXPENSE, "3200.00", "Electricity Bill", "State Grid", today.minusDays(24), true, "demo-utilities-1"),
                buildTransaction(demoUser, account, travel, Transaction.TransactionType.EXPENSE, "950.00", "Metro Recharge", "City Metro", today.minusDays(18), false, "demo-travel-1"),
                buildTransaction(demoUser, account, shopping, Transaction.TransactionType.EXPENSE, "4200.00", "Shopping", "Lifestyle", today.minusDays(12), false, "demo-shopping-1"),
                buildTransaction(demoUser, account, food, Transaction.TransactionType.EXPENSE, "1420.00", "Lunch", "Swiggy", today.minusDays(8), false, "demo-food-2"),
                buildTransaction(demoUser, account, salary, Transaction.TransactionType.INCOME, "87000.00", "Monthly Salary", "FinTrack Payroll", today.minusDays(5), false, "demo-salary-2"),
                buildTransaction(demoUser, account, travel, Transaction.TransactionType.EXPENSE, "2800.00", "Fuel", "Indian Oil", today.minusDays(3), false, "demo-travel-2"),
                buildTransaction(demoUser, account, utilities, Transaction.TransactionType.EXPENSE, "1299.00", "Internet Bill", "Airtel", today.minusDays(1), true, "demo-utilities-2")
        );

        transactionRepository.saveAll(demoTransactions);
        log.info("Seeded {} demo transactions for {}", demoTransactions.size(), DEMO_EMAIL);
    }

    private Transaction buildTransaction(
            User user,
            Account account,
            Category category,
            Transaction.TransactionType type,
            String amount,
            String description,
            String merchant,
            LocalDate transactionDate,
            boolean recurring,
            String importHash
    ) {
        return Transaction.builder()
                .user(user)
                .account(account)
                .category(category)
                .type(type)
                .amount(new BigDecimal(amount))
                .description(description)
                .merchant(merchant)
                .notes("Seeded demo data")
                .transactionDate(transactionDate)
                .isRecurring(recurring)
                .importHash(importHash)
                .aiCategoryLabel(category != null ? category.getName() : null)
                .importSource("demo-seed")
                .tags("demo")
                .build();
    }

    private void seedDemoGoals(User demoUser, Account account, SavingsGoalRepository savingsGoalRepository) {
        LocalDate today = LocalDate.now();
        savingsGoalRepository.saveAll(List.of(
                SavingsGoal.builder()
                        .user(demoUser)
                        .account(account)
                        .name("Emergency Fund")
                        .description("Build a six-month cash cushion.")
                        .targetAmount(new BigDecimal("150000.00"))
                        .currentAmount(new BigDecimal("72000.00"))
                        .monthlyContribution(new BigDecimal("12000.00"))
                        .targetDate(today.plusMonths(7))
                        .icon("shield")
                        .color("#c8f000")
                        .status(SavingsGoal.GoalStatus.ACTIVE)
                        .build(),
                SavingsGoal.builder()
                        .user(demoUser)
                        .account(account)
                        .name("Goa Vacation")
                        .description("Save for flights, stay, and local travel.")
                        .targetAmount(new BigDecimal("60000.00"))
                        .currentAmount(new BigDecimal("23000.00"))
                        .monthlyContribution(new BigDecimal("7000.00"))
                        .targetDate(today.plusMonths(6))
                        .icon("plane")
                        .color("#00e5ff")
                        .status(SavingsGoal.GoalStatus.ACTIVE)
                        .build()
        ));
        log.info("Seeded demo savings goals for {}", DEMO_EMAIL);
    }

    private void seedRecurringTransactions(
            User demoUser,
            Account account,
            CategoryRepository categoryRepository,
            RecurringTransactionRepository recurringTransactionRepository
    ) {
        Map<String, Category> categoriesByName = new HashMap<>();
        categoryRepository.findAllForUser(demoUser.getId()).forEach(category -> categoriesByName.put(category.getName(), category));
        LocalDate today = LocalDate.now();

        recurringTransactionRepository.saveAll(List.of(
                RecurringTransaction.builder()
                        .user(demoUser)
                        .account(account)
                        .category(categoriesByName.get("Rent"))
                        .type(Transaction.TransactionType.EXPENSE)
                        .amount(new BigDecimal("22000.00"))
                        .description("Apartment Rent")
                        .frequency(RecurringTransaction.Frequency.MONTHLY)
                        .startDate(today.minusMonths(4))
                        .endDate(null)
                        .nextDueDate(today.plusDays(5))
                        .lastProcessedDate(today.minusMonths(1))
                        .isActive(true)
                        .autoCreate(true)
                        .build(),
                RecurringTransaction.builder()
                        .user(demoUser)
                        .account(account)
                        .category(categoriesByName.get("Utilities"))
                        .type(Transaction.TransactionType.EXPENSE)
                        .amount(new BigDecimal("1299.00"))
                        .description("Internet Bill")
                        .frequency(RecurringTransaction.Frequency.MONTHLY)
                        .startDate(today.minusMonths(8))
                        .endDate(null)
                        .nextDueDate(today.plusDays(12))
                        .lastProcessedDate(today.minusMonths(1))
                        .isActive(true)
                        .autoCreate(true)
                        .build()
        ));
        log.info("Seeded recurring transactions for {}", DEMO_EMAIL);
    }
}
