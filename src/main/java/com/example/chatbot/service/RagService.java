package com.example.chatbot.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chatbot.model.ChatMessage;
import com.example.chatbot.model.ChatSession;
import com.example.chatbot.repository.ChatMessageRepository;
import com.example.chatbot.repository.ChatSessionRepository;

import reactor.core.publisher.Flux;

/**
 * Step 4 — Query flow (RAG):
 * embed question -> retrieve top-8 chunks -> build context block with sources ->
 * system prompt (+ recent chat history) + user question -> company LLM -> answer.
 * Step 6.20 — Streaming; Step 6.21 — chat history with per-answer source logging.
 */
@Service
public class RagService {

    // Step 4.13 — system prompt: answer only from context, admit gaps, cite sources
    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions using ONLY the context provided below.

            Rules:
            - Answer using only information found in the context.
            - If the answer is not in the context, say "I don't have that information in the uploaded documents." Do not make anything up.
            - Cite the source filename(s) you used at the end of your answer, e.g. "Sources: handbook.pdf".

            Context:
            %s
            """;

    private static final int HISTORY_TURNS = 10;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore,
            ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    /** Blocking ask (kept for the JSON API). */
    @Transactional
    public RagAnswer ask(UUID sessionId, String question, String department) {
        UUID session = ensureSession(sessionId, department);
        Retrieval retrieval = retrieve(question, department);
        if (retrieval == null) {
            return new RagAnswer(session,
                    "I don't have any documents for the " + department
                            + " department yet. Please upload documents first.",
                    List.of());
        }

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(retrieval.context()))
                .messages(historyMessages(session))
                .user(question)
                .call()
                .content();

        persistTurn(session, question, answer, retrieval.sources());
        return new RagAnswer(session, answer, List.copyOf(retrieval.sources()));
    }

    /**
     * Streaming ask (Step 6.20): returns the token stream; persists the full
     * answer once the stream completes.
     */
    public StreamingAnswer askStream(UUID sessionId, String question, String department) {
        UUID session = ensureSession(sessionId, department);
        Retrieval retrieval = retrieve(question, department);
        if (retrieval == null) {
            String fallback = "I don't have any documents for the " + department
                    + " department yet. Please upload documents first.";
            return new StreamingAnswer(session, Flux.just(fallback), List.of());
        }

        StringBuilder full = new StringBuilder();
        Flux<String> tokens = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(retrieval.context()))
                .messages(historyMessages(session))
                .user(question)
                .stream()
                .content()
                .doOnNext(full::append)
                .doOnComplete(() -> persistTurn(session, question, full.toString(), retrieval.sources()));

        return new StreamingAnswer(session, tokens, List.copyOf(retrieval.sources()));
    }

    public List<ChatMessage> history(UUID sessionId) {
        return messageRepository.findBySessionIdOrderByIdAsc(sessionId);
    }

    private UUID ensureSession(UUID sessionId, String department) {
        if (sessionId != null && sessionRepository.existsById(sessionId)) {
            return sessionId;
        }
        UUID id = UUID.randomUUID();
        sessionRepository.save(new ChatSession(id, Instant.now(), department));
        return id;
    }

    /**
     * Retrieve top-8 chunks scoped to the department (metadata filter — the
     * same mechanism Step 5 will use with JWT group claims); null when empty.
     */
    private Retrieval retrieve(String question, String department) {
        List<Document> chunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(8)
                        .filterExpression(new FilterExpressionBuilder()
                                .eq("department", department)
                                .build())
                        .build());
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        String context = chunks.stream()
                .map(c -> "[Source: %s]%n%s".formatted(
                        c.getMetadata().getOrDefault("filename", "unknown"), c.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));
        Set<String> sources = chunks.stream()
                .map(c -> String.valueOf(c.getMetadata().getOrDefault("filename", "unknown")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Retrieval(context, sources);
    }

    /** Step 6.21 — include recent turns so follow-up questions have context. */
    private List<Message> historyMessages(UUID sessionId) {
        List<ChatMessage> recent = messageRepository.findTop10BySessionIdOrderByIdDesc(sessionId);
        Collections.reverse(recent);
        List<Message> messages = new ArrayList<>(HISTORY_TURNS);
        for (ChatMessage m : recent) {
            messages.add("user".equals(m.getRole())
                    ? new UserMessage(m.getContent())
                    : new AssistantMessage(m.getContent()));
        }
        return messages;
    }

    private void persistTurn(UUID sessionId, String question, String answer, Set<String> sources) {
        Instant now = Instant.now();
        messageRepository.save(new ChatMessage(sessionId, "user", question, null, now));
        messageRepository.save(new ChatMessage(sessionId, "assistant", answer,
                String.join(",", sources), now));
    }

    private record Retrieval(String context, Set<String> sources) {
    }

    public record RagAnswer(UUID sessionId, String answer, List<String> sources) {
    }

    public record StreamingAnswer(UUID sessionId, Flux<String> tokens, List<String> sources) {
    }
}
