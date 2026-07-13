package com.example.chatbot.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.chatbot.model.DocumentEntity;
import com.example.chatbot.service.IngestionService;

/**
 * Step 3.11 — Upload endpoint; Step 6.22 — list and delete endpoints.
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
            DocumentEntity doc = ingestionService.ingest(file.getResource(), filename);
            return ResponseEntity.ok(Map.of(
                    "id", doc.getId(),
                    "filename", doc.getFilename(),
                    "chunksStored", doc.getChunkCount(),
                    "status", "ingested"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ingestion failed: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(ingestionService.listDocuments());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        if (ingestionService.deleteDocument(id)) {
            return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
        }
        return ResponseEntity.notFound().build();
    }
}
