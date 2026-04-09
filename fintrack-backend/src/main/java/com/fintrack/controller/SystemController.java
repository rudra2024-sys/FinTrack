package com.fintrack.controller;

import com.fintrack.config.DemoDataConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SystemController {

    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("message", "FinTrack backend is running");
        response.put("docs", "/api/swagger-ui.html");
        response.put("health", "/api/health");
        response.put("demoEmail", DemoDataConfig.DEMO_EMAIL);
        response.put("demoPassword", DemoDataConfig.DEMO_PASSWORD);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "fintrack-backend"
        ));
    }
}
