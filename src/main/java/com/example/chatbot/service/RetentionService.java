package com.example.chatbot.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chatbot.repository.ChatMessageRepository;

/**
 * Step 6.23 — Retention policy: chat messages older than the configured
 * number of days are deleted daily. Default 90 days; set chatbot.retention.days
 * (or CHATBOT_RETENTION_DAYS) to change; check with compliance before changing.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final ChatMessageRepository messageRepository;
    private final int retentionDays;

    public RetentionService(ChatMessageRepository messageRepository,
            @Value("${chatbot.retention.days:90}") int retentionDays) {
        this.messageRepository = messageRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 3 * * *") // daily at 03:00
    @Transactional
    public void purgeExpiredMessages() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = messageRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Retention: deleted {} chat messages older than {} days", deleted, retentionDays);
        }
    }

    public int getRetentionDays() {
        return retentionDays;
    }
}
