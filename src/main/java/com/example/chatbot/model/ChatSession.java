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

    // Nullable so ddl-auto:update can add the column to pre-existing rows
    @Column
    private String department;

    protected ChatSession() {
    }

    public ChatSession(UUID id, Instant createdAt, String department) {
        this.id = id;
        this.createdAt = createdAt;
        this.department = department;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDepartment() {
        return department;
    }
}
