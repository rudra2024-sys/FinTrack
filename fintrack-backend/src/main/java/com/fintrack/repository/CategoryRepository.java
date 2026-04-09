package com.fintrack.repository;

import com.fintrack.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Returns system categories + user's own custom categories
    @Query("SELECT c FROM Category c WHERE c.user IS NULL OR c.user.id = :userId ORDER BY c.isSystem DESC, c.name")
    List<Category> findAllForUser(@Param("userId") Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);
}
