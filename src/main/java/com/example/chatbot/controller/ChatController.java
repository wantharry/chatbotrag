package com.example.chatbot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.chatbot.service.RagService;

/**
 * Step 4.14 — Chat endpoint: POST /api/chat with JSON body {"question": "..."}.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body("Field 'question' is required");
        }
        RagService.RagAnswer result = ragService.ask(request.question().trim());
        return ResponseEntity.ok(result);
    }

    public record ChatRequest(String question) {
    }
}
