package com.fintrack.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Proxy controller that forwards fuzzy-logic requests to the Python ML microservice.
 * All endpoints are under /api/ml/fuzzy/ to avoid collisions with existing routes.
 */
@RestController
@RequestMapping("/ml/fuzzy")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fuzzy Logic Engine", description = "Proxy to Python fuzzy inference system (FIS) endpoints")
public class FuzzyProxyController {

    @Value("${app.ml.enabled:false}")
    private boolean mlEnabled;

    @Value("${app.ml.base-url:http://localhost:8001}")
    private String mlBaseUrl;

    // ── GET: membership functions (cached in Python) ──────────────────────────

    @GetMapping("/membership-functions")
    @Operation(summary = "Retrieve membership function curves for all FIS variables")
    public ResponseEntity<JsonNode> membershipFunctions() {
        if (!mlEnabled) {
            return ResponseEntity.ok(buildDisabledResponse());
        }
        try {
            JsonNode result = RestClient.create(mlBaseUrl)
                    .get()
                    .uri("/ml/fuzzy/membership-functions")
                    .retrieve()
                    .body(JsonNode.class);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.warn("[FuzzyProxy] membership-functions unavailable: {}", ex.getMessage());
            return ResponseEntity.ok(buildDisabledResponse());
        }
    }

    // ── POST: financial risk scoring ──────────────────────────────────────────

    @PostMapping("/risk")
    @Operation(summary = "Score financial risk using Mamdani fuzzy inference")
    public ResponseEntity<JsonNode> fuzzyRisk(@RequestBody Map<String, Object> body) {
        return proxyPost("/ml/fuzzy/risk", body);
    }

    // ── POST: budget alert ────────────────────────────────────────────────────

    @PostMapping("/budget-alert")
    @Operation(summary = "Generate fuzzy budget alert levels (Safe / Caution / Warning / Critical)")
    public ResponseEntity<JsonNode> budgetAlert(@RequestBody Map<String, Object> body) {
        return proxyPost("/ml/fuzzy/budget-alert", body);
    }

    // ── POST: savings advisor ─────────────────────────────────────────────────

    @PostMapping("/savings-advisor")
    @Operation(summary = "Recommend a savings strategy (Conservative / Moderate / Aggressive)")
    public ResponseEntity<JsonNode> savingsAdvisor(@RequestBody Map<String, Object> body) {
        return proxyPost("/ml/fuzzy/savings-advisor", body);
    }

    // ── POST: anomaly severity ────────────────────────────────────────────────

    @PostMapping("/anomaly-severity")
    @Operation(summary = "Score transaction anomaly severity (Mild / Moderate / Severe)")
    public ResponseEntity<JsonNode> anomalySeverity(@RequestBody Map<String, Object> body) {
        return proxyPost("/ml/fuzzy/anomaly-severity", body);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> proxyPost(String path, Object body) {
        if (!mlEnabled) {
            return ResponseEntity.ok(buildDisabledResponse());
        }
        try {
            JsonNode result = RestClient.create(mlBaseUrl)
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.warn("[FuzzyProxy] {} unavailable: {}", path, ex.getMessage());
            return ResponseEntity.ok(buildDisabledResponse());
        }
    }

    private JsonNode buildDisabledResponse() {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode()
                .put("error", "ML service is disabled or unavailable");
    }
}
