package com.example.chatbot.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Step 6.21 — Chat history: one row per conversation.
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatSession() {
    }

    public ChatSession(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
