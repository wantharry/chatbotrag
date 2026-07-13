package com.example.chatbot.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chatbot.model.DocumentEntity;
import com.example.chatbot.repository.DocumentRepository;

/**
 * Step 3 — Ingestion flow:
 * parse (Tika) -> split (~300-token chunks) -> attach metadata -> embed + store (pgvector).
 * Step 6.22 — Document management: registry table + delete that removes chunks too.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;

    // ~300-token chunks: must stay within the all-MiniLM-L6-v2 embedding window (~512
    // tokens); larger chunks get silently truncated and their tail becomes unsearchable
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(300)
            .withMinChunkSizeChars(150)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build();

    public IngestionService(VectorStore vectorStore, DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public DocumentEntity ingest(Resource file, String filename, String department) {
        // 1. Parse the uploaded file (PDF, DOCX, TXT, HTML, ...)
        TikaDocumentReader reader = new TikaDocumentReader(file);
        List<Document> parsed = reader.get();

        // 2. Split into ~300-token chunks
        List<Document> chunks = splitter.apply(parsed);

        // 3. Attach metadata; docId links chunks to the registry row,
        //    department scopes retrieval (see RagService)
        UUID docId = UUID.randomUUID();
        Instant uploadedAt = Instant.now();
        chunks.forEach(chunk -> {
            chunk.getMetadata().put("docId", docId.toString());
            chunk.getMetadata().put("filename", filename);
            chunk.getMetadata().put("uploadedAt", uploadedAt.toString());
            chunk.getMetadata().put("department", department);
        });

        // 4. Embed and store in one step
        vectorStore.add(chunks);

        // 5. Register the document
        DocumentEntity entity = documentRepository.save(
                new DocumentEntity(docId, filename, uploadedAt, chunks.size(), department));

        log.info("Ingested '{}' (docId={}, department={}): {} chunks stored",
                filename, docId, department, chunks.size());
        return entity;
    }

    public List<DocumentEntity> listDocuments(String department) {
        return documentRepository.findByDepartmentOrderByUploadedAtDesc(department);
    }

    @Transactional
    public boolean deleteDocument(UUID docId) {
        if (!documentRepository.existsById(docId)) {
            return false;
        }
        // Remove chunks from the vector store, then the registry row
        vectorStore.delete(new FilterExpressionBuilder()
                .eq("docId", docId.toString())
                .build());
        documentRepository.deleteById(docId);
        log.info("Deleted document {} and its chunks", docId);
        return true;
    }
}
