package com.example.chatbot.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Step 4 — Query flow (RAG):
 * embed question -> retrieve top-8 chunks -> build context block with sources ->
 * system prompt + user question -> company LLM -> answer.
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

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public RagAnswer ask(String question) {
        // 1. Embed question + retrieve top-8 most similar chunks
        List<Document> chunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(8)
                        .build());

        if (chunks == null || chunks.isEmpty()) {
            return new RagAnswer(
                    "I don't have any documents to search yet. Please upload documents first.",
                    List.of());
        }

        // 2. Assemble context block with source filenames
        String context = chunks.stream()
                .map(c -> "[Source: %s]%n%s".formatted(
                        c.getMetadata().getOrDefault("filename", "unknown"), c.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        Set<String> sources = chunks.stream()
                .map(c -> String.valueOf(c.getMetadata().getOrDefault("filename", "unknown")))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 3. Send system prompt (with context) + user question to the LLM
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(context))
                .user(question)
                .call()
                .content();

        return new RagAnswer(answer, List.copyOf(sources));
    }

    public record RagAnswer(String answer, List<String> sources) {
    }
}
