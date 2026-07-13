package com.example.chatbot.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.chatbot.service.Departments;
import com.example.chatbot.service.RagService;

/**
 * Step 4.14 — POST /api/chat (blocking JSON).
 * Step 6.20 — POST /api/chat/stream (Server-Sent Events token stream).
 * Step 6.21 — GET /api/chat/{sessionId}/history.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 120_000;

    private final RagService ragService;
    private final Departments departments;

    public ChatController(RagService ragService, Departments departments) {
        this.ragService = ragService;
        this.departments = departments;
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'question' is required"));
        }
        String dept;
        try {
            dept = departments.validate(request.department());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        RagService.RagAnswer result = ragService.ask(request.sessionId(), request.question().trim(), dept);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        if (request.question() == null || request.question().isBlank()) {
            emitter.completeWithError(new IllegalArgumentException("Field 'question' is required"));
            return emitter;
        }
        String dept;
        try {
            dept = departments.validate(request.department());
        } catch (IllegalArgumentException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        RagService.StreamingAnswer answer = ragService.askStream(
                request.sessionId(), request.question().trim(), dept);

        // Send session + sources up front so the client can render metadata immediately
        try {
            emitter.send(SseEmitter.event().name("meta")
                    .data(Map.of("sessionId", answer.sessionId(), "sources", answer.sources())));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        answer.tokens().subscribe(
                token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
        return emitter;
    }

    @GetMapping("/{sessionId}/history")
    public ResponseEntity<?> history(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ragService.history(sessionId));
    }

    public record ChatRequest(UUID sessionId, String question, String department) {
    }
}
