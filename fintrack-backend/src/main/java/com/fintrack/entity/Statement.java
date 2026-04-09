package com.fintrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "statements")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    private String source;

    @CreatedDate
    @Column(name = "upload_date", nullable = false, updatable = false)
    private Instant uploadDate;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatementStatus status = StatementStatus.PROCESSED;

    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private Integer transactionCount = 0;

    @Column(name = "total_income", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalIncome = BigDecimal.ZERO;

    @Column(name = "total_expenses", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalExpenses = BigDecimal.ZERO;

    private String notes;

    public enum StatementStatus {
        RECEIVED, PROCESSING, PROCESSED, FAILED
    }
}

