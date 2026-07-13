package com.example.chatbot.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.example.chatbot.model.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop10BySessionIdOrderByIdDesc(UUID sessionId);

    List<ChatMessage> findBySessionIdOrderByIdAsc(UUID sessionId);

    @Modifying
    @Query("delete from ChatMessage m where m.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
