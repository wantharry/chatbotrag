# RAG Chatbot — Complete Design & Development Documentation

**Project location:** `/Users/vijjimuk/V_claude_code/Chatbot`
**Date:** July 13, 2026

---

## 1. What This App Is

A **RAG (Retrieval-Augmented Generation) chatbot**. You upload your own documents
(PDF, DOCX, TXT, HTML…), and then ask questions in a chat UI. The bot answers
**only from the content of your documents** — it does not make things up, it
admits when the answer isn't in the documents, and it cites the source file
for every answer.

Why RAG instead of just asking an LLM directly?

- LLMs don't know your private/company documents.
- LLMs hallucinate when asked about things outside their training data.
- RAG solves both: it *retrieves* the relevant passages from your documents and
  hands them to the LLM as context, so the LLM only has to *read and answer*,
  not *remember*.

---

## 2. The Plan (Original Step-by-Step Design)

| Step | Goal | Status |
|------|------|--------|
| 0 | Prerequisites: Java 17+, Docker, LLM endpoint + API key | ✅ Done |
| 1 | Spring Boot project + Spring AI dependencies | ✅ Done |
| 2 | Configuration: Postgres, LLM endpoint, pgvector, embeddings | ✅ Done |
| 3 | Ingestion flow: upload → parse → chunk → embed → store | ✅ Done |
| 4 | Query flow (RAG): question → retrieve → prompt → answer | ✅ Done |
| — | Bonus: browser chat UI at `/` | ✅ Done |
| 5 | SSO integration (OAuth2 resource server, JWT, group-based access) | ⏳ Planned |
| 6 | Production hardening: streaming, chat history, doc management, retention, eval set | ⏳ Planned |

---

## 3. Technology Stack & What Each Piece Does

| Component | Technology | Role |
|-----------|-----------|------|
| Web framework | **Spring Boot 3.4** (Java 17) | REST API + serves the chat UI |
| AI framework | **Spring AI 1.0** | Glue for LLM, embeddings, vector store, document parsing |
| LLM (the "brain") | **Groq** — `llama-3.3-70b-versatile` | Generates the final answers. Accessed through Groq's OpenAI-compatible API |
| Embedding model | **all-MiniLM-L6-v2** (ONNX, runs locally in the JVM) | Converts text into 384-dimension vectors for similarity search. Free, no API calls |
| Vector database | **PostgreSQL 16 + pgvector** (Docker container) | Stores text chunks and their vectors; performs similarity search |
| Document parser | **Apache Tika** | Extracts plain text from PDF, DOCX, TXT, HTML, and ~1000 other formats |
| Chat UI | Plain HTML/CSS/JS (`static/index.html`) | Browser interface with upload button and chat bubbles |

### Why two different AI models?

1. **Embedding model (local, small, fast, free)** — used constantly: every chunk of
   every document gets embedded, and every question gets embedded. Running it
   locally means zero cost and no data leaves the machine for this step.
2. **LLM (remote, large, powerful)** — used once per question to compose the
   final natural-language answer from the retrieved context.

---

## 4. Project Structure

```
Chatbot/
├── pom.xml                          # Maven build: Spring Boot + Spring AI dependencies
├── docker-compose.yml               # PostgreSQL + pgvector container
├── README.md                        # Quick-start guide
├── DOCUMENTATION.md                 # This file
└── src/main/
    ├── resources/
    │   ├── application.yml          # All configuration
    │   └── static/index.html        # Browser chat UI (served at /)
    └── java/com/example/chatbot/
        ├── ChatbotApplication.java              # Spring Boot entry point
        ├── controller/
        │   ├── DocumentController.java          # POST /api/documents/upload
        │   └── ChatController.java              # POST /api/chat
        └── service/
            ├── IngestionService.java            # Storage pipeline
            └── RagService.java                  # Retrieval + answer pipeline
```

### Key dependencies (`pom.xml`)

- `spring-ai-bom` — version alignment for all Spring AI modules
- `spring-ai-starter-model-openai` — OpenAI-compatible chat client (pointed at Groq)
- `spring-ai-starter-vector-store-pgvector` — auto-configures the `VectorStore` bean
- `spring-ai-tika-document-reader` — document parsing
- `spring-ai-starter-model-transformers` — local ONNX embedding model
- `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`

### Configuration (`application.yml`) — the important parts

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chatbot   # the Docker container
  ai:
    openai:
      base-url: https://api.groq.com/openai          # Groq's OpenAI-compatible endpoint
      api-key: ${GROQ_API_KEY}                       # from environment variable — never hardcoded
      chat:
        options:
          model: llama-3.3-70b-versatile
          temperature: 0.2                           # low = factual, less creative
      embedding:
        enabled: false                               # don't use OpenAI/Groq for embeddings
    model:
      embedding: transformers                        # use the local ONNX model instead
    vectorstore:
      pgvector:
        initialize-schema: true                      # auto-create table + index on startup
        dimensions: 384                              # must match all-MiniLM-L6-v2 output
        distance-type: COSINE_DISTANCE               # similarity metric
        index-type: HNSW                             # fast approximate nearest-neighbor index
```

---

## 5. HOW STORAGE WORKS (Ingestion Pipeline, in Detail)

Triggered by: `POST /api/documents/upload` (multipart file) → `DocumentController` → `IngestionService.ingest()`

```
 uploaded file
      │
      ▼
 ① PARSE  (TikaDocumentReader)
      │     Apache Tika detects the format (PDF/DOCX/TXT/HTML…) and extracts
      │     the raw plain text, discarding formatting, images, etc.
      ▼
 ② CHUNK  (TokenTextSplitter — ~300 tokens per chunk)
      │     The full text is split into small overlapping-boundary pieces.
      │     Why chunk at all?
      │       • The embedding model can only "read" ~512 tokens at once.
      │       • Retrieval works best on focused passages, not whole books.
      │     Example: greek_mythology.pdf (≈200 KB text) → 162 chunks.
      ▼
 ③ TAG   (metadata)
      │     Each chunk gets metadata attached:
      │       { "filename": "greek_mythology.pdf",
      │         "uploadedAt": "2026-07-13T19:32:11Z" }
      │     This is what lets answers cite their sources, and lets us
      │     delete/filter by document later.
      ▼
 ④ EMBED (all-MiniLM-L6-v2, local ONNX model)
      │     Each chunk's text is converted into a vector of 384 floating-point
      │     numbers. This vector is a mathematical "meaning fingerprint":
      │     texts about similar topics produce vectors that point in similar
      │     directions, even if they share no words.
      │       "Hera was tall and beautiful"   → [0.021, -0.117, 0.302, …]
      │       "Was the queen of gods short?"  → [0.019, -0.121, 0.298, …]  ← close!
      │       "Expense reports need receipts" → [-0.244, 0.067, -0.180, …] ← far away
      ▼
 ⑤ STORE (pgvector — vectorStore.add(chunks))
            Each chunk becomes one row in the vector_store table in Postgres.
```

### What the database actually looks like

Spring AI auto-creates this table (because `initialize-schema: true`):

```sql
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY,        -- generated per chunk
    content   TEXT,                    -- the chunk's raw text
    metadata  JSON,                    -- {"filename": "...", "uploadedAt": "..."}
    embedding VECTOR(384)              -- the 384-number meaning fingerprint
);
CREATE INDEX ... USING hnsw (embedding vector_cosine_ops);   -- fast search index
```

- **HNSW index** (Hierarchical Navigable Small World): a graph structure that finds
  the nearest vectors in milliseconds without comparing against every row —
  essential once you have thousands of chunks.
- **Persistence:** the data lives in a Docker volume (`pgdata`), so documents
  survive app restarts and container restarts.

---

## 6. HOW RETRIEVAL WORKS (Query Pipeline, in Detail)

Triggered by: `POST /api/chat` with `{"question": "..."}` → `ChatController` → `RagService.ask()`

```
 "Was Hera tall or short?"
      │
      ▼
 ① EMBED THE QUESTION
      │     The same local embedding model converts the question into a
      │     384-dim vector — the question's own "meaning fingerprint".
      ▼
 ② SIMILARITY SEARCH (vectorStore.similaritySearch, topK = 8)
      │     pgvector compares the question vector against every stored chunk
      │     vector using COSINE DISTANCE (the angle between vectors):
      │
      │         similarity = cos(θ) between question vector and chunk vector
      │         1.0 = identical meaning ... 0.0 = unrelated
      │
      │     The HNSW index makes this fast. The 8 closest chunks win.
      │     KEY INSIGHT: this matches by MEANING, not keywords — a question
      │     about "tall or short" finds a chunk saying "the tall and beautiful
      │     goddess Hera" even though the word "short" never appears in it.
      ▼
 ③ BUILD THE CONTEXT BLOCK
      │     The 8 chunks are concatenated with source labels:
      │
      │         [Source: greek_mythology.pdf]
      │         CHAPTER V — HERA, THE QUEEN OF THE GODS
      │         The wife of Zeus was the tall and beautiful goddess Hera...
      │         ---
      │         [Source: greek_mythology.pdf]
      │         She was the greatest of all the goddesses...
      ▼
 ④ PROMPT THE LLM (Groq / llama-3.3-70b-versatile)
      │     Two messages are sent:
      │
      │     SYSTEM MESSAGE (the rules + the context):
      │         "You are a helpful assistant that answers questions using
      │          ONLY the context provided below.
      │          - Answer using only information found in the context.
      │          - If the answer is not in the context, say 'I don't have
      │            that information in the uploaded documents.'
      │          - Cite the source filename(s).
      │          Context: <the block from step ③>"
      │
      │     USER MESSAGE:
      │         "Was Hera tall or short?"
      ▼
 ⑤ ANSWER
            {"answer": "Hera was described as 'the tall and beautiful
                        goddess'. Sources: greek_mythology.pdf",
             "sources": ["greek_mythology.pdf"]}
```

### Why the system prompt matters (grounding)

The three rules in the system prompt are what turn a generic LLM into a
trustworthy document assistant:

1. **"Answer ONLY from context"** → prevents the LLM from using its training
   data (which may be wrong or irrelevant for your documents).
2. **"Admit when the answer isn't there"** → prevents hallucination. Verified:
   asking "What is the dress code?" against the mythology book correctly returns
   "I don't have that information in the uploaded documents."
3. **"Cite sources"** → users can verify every answer.

`temperature: 0.2` further reduces creativity/randomness in favor of factual precision.

---

## 7. How It Was Developed (Timeline & Decisions)

### Phase 1 — Project scaffolding
Created `pom.xml` with the Spring AI BOM and the four AI starters, wrote
`application.yml`, the two controllers, and the two services. Also wrote
`docker-compose.yml` for the pgvector container.

**Security decision:** the Groq API key is read from the `GROQ_API_KEY`
environment variable and is never written into any source or config file.

### Phase 2 — Environment setup (this machine had nothing installed)
Homebrew existed but was permission-locked (needed sudo). Everything was
installed **user-locally, no admin rights required**:

| Tool | Version | Installed to |
|------|---------|--------------|
| Temurin JDK | 17.0.19 | `~/tools/jdk-17.0.19+10` |
| Maven | 3.9.16 | `~/tools/apache-maven-3.9.16` |
| Docker CLI | 28.3.2 | `~/tools/bin/docker` |
| Colima (container runtime VM) | 0.10.3 | `~/tools/bin/colima` |
| Lima (VM backend) | 2.1.4 | `~/tools/lima` |

Notes:
- Docker Desktop wasn't runnable, so **Colima** provides the Linux VM that
  actually runs containers; the plain Docker CLI talks to it (`docker context use colima`).
- The Docker Desktop credential helper entry in `~/.docker/config.json` broke
  image pulls and was removed (backup: `config.json.bak`).
- PATH entries were persisted in `~/.zshrc`.

### Phase 3 — First run & end-to-end verification
- `docker compose up -d` started `chatbot-postgres` (pgvector/pg16, port 5432).
- App started with `mvn spring-boot:run`; on first boot Spring AI downloaded
  the ONNX embedding model and pgvector auto-created the `vector_store` table.
- Verified with a test handbook: upload worked, in-context questions answered
  correctly, out-of-context questions correctly refused.

### Phase 4 — Chat UI
Added `static/index.html` — Spring Boot serves it automatically at
`http://localhost:8080/`. Upload button + chat bubbles + source citations,
calling the same two REST endpoints via `fetch()`.

### Phase 5 — The "Hera bug" and the chunking fix (important lesson)

**Symptom:** after uploading `greek_mythology.pdf`, the question *"Was Hera
tall or short?"* returned "I don't have that information" — even though the
book literally says *"the tall and beautiful goddess Hera."*

**Diagnosis:** the ingestion pipeline used **~800-token chunks**, but the
all-MiniLM-L6-v2 embedding model only processes the **first ~512 tokens** of
its input — anything beyond that is *silently truncated*. The Hera sentence
sat in the back half of a large chunk, so its content never made it into the
chunk's vector. The chunk was stored, but it was **unsearchable by meaning**.

**Fix:**
1. Chunk size reduced **800 → 300 tokens** (`IngestionService`) so every chunk
   fits entirely inside the embedding window. The PDF went from 60 → 162 chunks.
2. Retrieval widened **top-5 → top-8** (`RagService`) since smaller chunks
   carry less context each.
3. Old chunks deleted from the DB, PDF re-ingested.

**Result:** both the height question and other detail questions ("What bird
did Hera choose as her favorite?") now answer correctly with citations.

**Lesson:** chunk size must never exceed the embedding model's context window;
otherwise storage silently loses meaning.

---

## 8. API Reference

### Upload a document
```bash
curl -F "file=@/path/to/document.pdf" http://localhost:8080/api/documents/upload
# → {"filename":"document.pdf","chunksStored":162,"status":"ingested"}
```

### Ask a question
```bash
curl -H "Content-Type: application/json" \
     -d '{"question":"Was Hera tall or short?"}' \
     http://localhost:8080/api/chat
# → {"answer":"Hera was described as 'the tall and beautiful goddess'. ...",
#    "sources":["greek_mythology.pdf"]}
```

### Browser UI
Open `http://localhost:8080/` — upload documents and chat interactively.

---

## 9. How to Run Everything

```bash
# 1. Start the container runtime + database
colima start
cd /Users/vijjimuk/V_claude_code/Chatbot
docker compose up -d

# 2. Set the LLM API key (never commit it)
export GROQ_API_KEY=<your-groq-api-key>

# 3. Run the app
mvn spring-boot:run

# 4. Open http://localhost:8080/
```

Useful checks:
```bash
docker ps                                    # is chatbot-postgres up?
tail -f /tmp/chatbot.log                     # app logs (if started detached)
docker exec chatbot-postgres psql -U chatbot -d chatbot \
  -c "SELECT metadata->>'filename', count(*) FROM vector_store GROUP BY 1;"
                                             # what's ingested?
```

---

## 10. Current Limitations & Roadmap (Steps 5–6)

| Item | Description |
|------|-------------|
| **SSO / auth (Step 5)** | No authentication yet — anyone on localhost can use it. Plan: `spring-boot-starter-oauth2-resource-server`, JWT validation against a company identity provider, per-user document access via metadata `filterExpression` on retrieval |
| **Streaming** | Answers arrive all at once. Plan: `.stream()` → `Flux<String>` over Server-Sent Events for token-by-token display |
| **Chat history / memory** | Each question is independent; no follow-up context. Plan: `chat_sessions` + `chat_messages` tables keyed by user, Spring AI `ChatMemory` |
| **Document management** | No list/delete endpoints yet (chunks can only be deleted via SQL). Plan: a `documents` JPA table + delete endpoint that also removes chunks |
| **Retention & compliance** | No retention policy for stored content/history yet |
| **Evaluation set** | Plan: 20–30 real Q&A pairs, re-tested after any tuning change (chunk size, topK, prompt, model) |
| **Duplicate uploads** | Uploading the same file twice stores its chunks twice (harmless for answers, wasteful for storage) |
