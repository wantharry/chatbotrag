package com.example.chatbot.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Step 3 — Ingestion flow:
 * parse (Tika) -> split (~300-token chunks) -> attach metadata -> embed + store (pgvector).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;

    // ~300-token chunks: must stay within the all-MiniLM-L6-v2 embedding window (~512
    // tokens); larger chunks get silently truncated and their tail becomes unsearchable
    private final TokenTextSplitter splitter = new TokenTextSplitter(300, 150, 5, 10000, true);

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int ingest(Resource file, String filename) {
        // 1. Parse the uploaded file (PDF, DOCX, TXT, HTML, ...)
        TikaDocumentReader reader = new TikaDocumentReader(file);
        List<Document> parsed = reader.get();

        // 2. Split into ~300-token chunks
        List<Document> chunks = splitter.apply(parsed);

        // 3. Attach metadata
        String uploadedAt = Instant.now().toString();
        chunks.forEach(chunk -> {
            chunk.getMetadata().put("filename", filename);
            chunk.getMetadata().put("uploadedAt", uploadedAt);
        });

        // 4. Embed and store in one step
        vectorStore.add(chunks);

        log.info("Ingested '{}': {} chunks stored", filename, chunks.size());
        return chunks.size();
    }
}
