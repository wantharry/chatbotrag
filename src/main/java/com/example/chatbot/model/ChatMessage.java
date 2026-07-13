package com.example.chatbot.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Step 6.21 — Chat history: one row per message (question or answer),
 * with retrieved sources logged for answers.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private String role; // "user" or "assistant"

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String sources; // comma-separated filenames, for assistant messages

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(UUID sessionId, String role, String content, String sources, Instant createdAt) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.sources = sources;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSources() {
        return sources;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
