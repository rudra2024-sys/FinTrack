package com.fintrack.dto.account;

import com.fintrack.entity.Account.AccountType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class AccountDTOs {

    public record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type,
            BigDecimal initialBalance,
            String currency,
            String institution,
            String color
    ) {}

    public record UpdateRequest(
            @Size(max = 100) String name,
            String institution,
            String color,
            Boolean isActive
    ) {}

    public record Response(
            Long id,
            String name,
            AccountType type,
            BigDecimal balance,
            String currency,
            String institution,
            String color,
            Boolean isActive
    ) {}
}
