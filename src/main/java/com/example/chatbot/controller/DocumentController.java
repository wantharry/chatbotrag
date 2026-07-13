package com.example.chatbot.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.chatbot.service.IngestionService;

/**
 * Step 3.11 — Upload endpoint: POST /api/documents/upload (multipart/form-data, field "file").
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final IngestionService ingestionService;

    public DocumentController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        try {
            int chunkCount = ingestionService.ingest(file.getResource(), filename);
            return ResponseEntity.ok(Map.of(
                    "filename", filename,
                    "chunksStored", chunkCount,
                    "status", "ingested"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ingestion failed: " + e.getMessage()));
        }
    }
}
