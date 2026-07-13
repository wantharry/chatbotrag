# RAG Chatbot (Spring Boot + Spring AI + pgvector + Groq)

A retrieval-augmented-generation chatbot: upload documents, then ask questions answered from those documents.

## Architecture

- **LLM**: Groq (`llama-3.3-70b-versatile`) via the OpenAI-compatible API
- **Embeddings**: local ONNX model (all-MiniLM-L6-v2, 384 dims) — no API cost, runs in-process
- **Vector store**: PostgreSQL + pgvector (cosine distance, HNSW index)
- **Parsing**: Apache Tika (PDF, DOCX, TXT, HTML, ...)

## Prerequisites (Step 0)

- Java 17+ and Maven (e.g. `brew install openjdk@17 maven`)
- Docker (for PostgreSQL + pgvector)

## Run

1. Start the database:

   ```bash
   docker compose up -d
   ```

2. Set your Groq API key (never commit it):

   ```bash
   export GROQ_API_KEY=<your-groq-api-key>
   ```

3. Start the app:

   ```bash
   mvn spring-boot:run
   ```

   First startup downloads the ONNX embedding model (~90 MB).

## API

### Upload a document

```bash
curl -F "file=@/path/to/handbook.pdf" http://localhost:8080/api/documents/upload
```

Response: `{"filename":"handbook.pdf","chunksStored":42,"status":"ingested"}`

### Ask a question

```bash
curl -H "Content-Type: application/json" \
     -d '{"question":"What is the vacation policy?"}' \
     http://localhost:8080/api/chat
```

Response: `{"answer":"...","sources":["handbook.pdf"]}`

## Roadmap (not yet implemented)

- Step 5: SSO / OAuth2 resource server + group-based retrieval filtering
- Step 6: streaming responses, chat history, document management, retention policy, evaluation set
