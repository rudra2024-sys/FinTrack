package com.fintrack.controller;

import com.fintrack.dto.analytics.AnalyticsDTOs.*;
import com.fintrack.security.SecurityUtils;
import com.fintrack.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Dashboard data, trends, and category breakdown")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SecurityUtils securityUtils;

    @GetMapping("")
    @Operation(summary = "Get full dashboard summary (default analytics endpoint)")
    public ResponseEntity<DashboardSummary> getAnalytics() {
        return ResponseEntity.ok(analyticsService.getDashboard(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get full dashboard summary for current month")
    public ResponseEntity<DashboardSummary> getDashboard() {
        return ResponseEntity.ok(analyticsService.getDashboard(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/monthly-trend")
    @Operation(summary = "Get 12-month income vs expense trend")
    public ResponseEntity<List<MonthlyData>> getMonthlyTrend() {
        return ResponseEntity.ok(analyticsService.getMonthlyTrend(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/category-breakdown")
    @Operation(summary = "Get spending breakdown by category for a date range")
    public ResponseEntity<List<CategorySpend>> getCategoryBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(analyticsService.getCategoryBreakdown(
                securityUtils.getCurrentUserId(), startDate, endDate));
    }

    @GetMapping("/category")
    @Operation(summary = "Alias for category breakdown to support AI analytics consumers")
    public ResponseEntity<List<CategorySpend>> getCategoryBreakdownAlias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(analyticsService.getCategoryBreakdown(
                securityUtils.getCurrentUserId(), startDate, endDate));
    }
}
