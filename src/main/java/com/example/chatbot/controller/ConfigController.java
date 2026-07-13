package com.example.chatbot.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.chatbot.service.Departments;
import com.example.chatbot.service.RetentionService;

/** Exposes settings the UI needs (retention notice — Step 6.23; department list). */
@RestController
public class ConfigController {

    private final RetentionService retentionService;
    private final Departments departments;

    public ConfigController(RetentionService retentionService, Departments departments) {
        this.retentionService = retentionService;
        this.departments = departments;
    }

    @GetMapping("/api/config")
    public Map<String, Object> config() {
        return Map.of(
                "retentionDays", retentionService.getRetentionDays(),
                "departments", departments.list());
    }
}
