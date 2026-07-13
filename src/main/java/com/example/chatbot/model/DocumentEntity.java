package com.example.chatbot.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Step 6.22 — Document registry: one row per uploaded file, linked to its
 * vector_store chunks via the "docId" metadata key.
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private Instant uploadedAt;

    @Column(nullable = false)
    private int chunkCount;

    // Nullable so ddl-auto:update can add the column to pre-existing rows
    @Column
    private String department;

    protected DocumentEntity() {
    }

    public DocumentEntity(UUID id, String filename, Instant uploadedAt, int chunkCount, String department) {
        this.id = id;
        this.filename = filename;
        this.uploadedAt = uploadedAt;
        this.chunkCount = chunkCount;
        this.department = department;
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getDepartment() {
        return department;
    }
}
