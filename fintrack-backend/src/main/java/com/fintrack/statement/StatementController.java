package com.fintrack.statement;

import com.fintrack.dto.statement.StatementDTOs.*;
import com.fintrack.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/statements")
@RequiredArgsConstructor
@Tag(name = "Statements", description = "Upload and process CSV or PDF bank statements")
public class StatementController {

    private final StatementService statementService;
    private final SecurityUtils securityUtils;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CSV or machine-readable PDF bank statement for parsing and AI categorization")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("accountId") Long accountId,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "statementDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate,
            @RequestParam(value = "applyToAccountBalance", defaultValue = "false") boolean applyToAccountBalance
    ) {
        return ResponseEntity.ok(statementService.upload(
                securityUtils.getCurrentUserId(),
                accountId,
                file,
                source,
                statementDate,
                applyToAccountBalance
        ));
    }

    @GetMapping
    @Operation(summary = "List processed statements for current user")
    public ResponseEntity<ListResponse> list() {
        return ResponseEntity.ok(statementService.listStatements(securityUtils.getCurrentUserId()));
    }
}
