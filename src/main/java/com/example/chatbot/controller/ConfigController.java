package com.example.chatbot.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.chatbot.service.RetentionService;

/** Exposes settings the UI needs (e.g., the retention notice — Step 6.23). */
@RestController
public class ConfigController {

    private final RetentionService retentionService;

    public ConfigController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @GetMapping("/api/config")
    public Map<String, Object> config() {
        return Map.of("retentionDays", retentionService.getRetentionDays());
    }
}
