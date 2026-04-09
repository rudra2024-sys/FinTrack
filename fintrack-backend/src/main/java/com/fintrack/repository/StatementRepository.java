package com.fintrack.repository;

import com.fintrack.entity.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Long> {
    List<Statement> findByUserIdOrderByUploadDateDesc(Long userId);
}

