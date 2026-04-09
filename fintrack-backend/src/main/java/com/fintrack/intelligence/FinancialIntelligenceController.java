package com.fintrack.intelligence;

import com.fintrack.dto.intelligence.IntelligenceDTOs.*;
import com.fintrack.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/intelligence")
@RequiredArgsConstructor
@Tag(name = "Financial Intelligence", description = "AI-powered financial intelligence, HMM behavior analysis, and fuzzy risk scoring")
public class FinancialIntelligenceController {

    private final FinancialIntelligenceService financialIntelligenceService;
    private final SecurityUtils securityUtils;

    @PostMapping("/analyze")
    @Operation(summary = "Analyze the current user's transactions with HMM, fuzzy logic, categorization, and alerts")
    public ResponseEntity<IntelligenceResponse> analyze(@RequestBody(required = false) AnalyzeRequest request) {
        AnalyzeRequest effectiveRequest = request != null ? request : new AnalyzeRequest(null, null, null, null, null);
        return ResponseEntity.ok(
                financialIntelligenceService.analyzeUserTransactions(securityUtils.getCurrentUserId(), effectiveRequest)
        );
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract Google Pay PDF transactions and return intelligent financial insights")
    public ResponseEntity<PdfIntelligenceResponse> analyzePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "incomeStability", required = false) Double incomeStability,
            @RequestParam(value = "savingsRate", required = false) Double savingsRate,
            @RequestParam(value = "debtPressure", required = false) Double debtPressure
    ) {
        return ResponseEntity.ok(
                financialIntelligenceService.analyzePdf(file, incomeStability, savingsRate, debtPressure)
        );
    }
}
