package com.fintrack.insights;

import com.fintrack.dto.insights.InsightDTOs.InsightsResponse;
import com.fintrack.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/insights")
@RequiredArgsConstructor
@Tag(name = "Insights", description = "AI-driven financial insights and predictions")
public class InsightsController {

    private final InsightService insightService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "Get anomalies, spending patterns, predictions, and personalized recommendations")
    public ResponseEntity<InsightsResponse> getInsights() {
        return ResponseEntity.ok(insightService.getInsights(securityUtils.getCurrentUserId()));
    }
}
