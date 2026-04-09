package com.fintrack.repository;

import com.fintrack.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserIdAndIsActiveTrue(Long userId);
    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
