package com.example.chatbot.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.chatbot.model.DocumentEntity;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findByDepartmentOrderByUploadedAtDesc(String department);
}
