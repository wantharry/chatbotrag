# RAG Chatbot (Spring Boot + Spring AI + pgvector + Groq/Gemini)

A retrieval-augmented-generation chatbot: upload documents, then ask questions answered from those documents.

## Architecture

- **LLM**: Groq (`llama-3.3-70b-versatile`) by default, or Google Gemini (`gemini-2.5-flash`) via the `gemini` Spring profile — both through the OpenAI-compatible API
- **Embeddings**: local ONNX model (all-MiniLM-L6-v2, 384 dims) — no API cost, runs in-process
- **Vector store**: PostgreSQL + pgvector (cosine distance, HNSW index)
- **Parsing**: Apache Tika (PDF, DOCX, TXT, HTML, ...)
- **Departments**: uploads and questions are scoped to a department (`General`, `HR`, `Engineering`, `Finance` — configurable via `chatbot.departments`); retrieval never crosses department boundaries

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

   Or run against Google Gemini instead (e.g. when Groq's free daily quota is exhausted):

   ```bash
   export GOOGLE_API_KEY=<your-google-ai-studio-key>
   mvn spring-boot:run -Dspring-boot.run.profiles=gemini
   ```

   First startup downloads the ONNX embedding model (~90 MB) to `~/.cache/chatbot-onnx`.

4. Open the chat UI at http://localhost:8080/

## API

### Upload / list / delete documents

```bash
curl -F "file=@/path/to/handbook.pdf" -F "department=HR" http://localhost:8080/api/documents/upload
# → {"filename":"handbook.pdf","chunksStored":42,"status":"ingested","id":"<uuid>","department":"HR"}

curl "http://localhost:8080/api/documents?department=HR"     # list a department's uploads
curl -X DELETE http://localhost:8080/api/documents/<id>   # removes chunks too
```

### Ask a question

```bash
curl -H "Content-Type: application/json" \
     -d '{"question":"What is the vacation policy?","department":"HR"}' \
     http://localhost:8080/api/chat
```

Response: `{"sessionId":"<uuid>","answer":"...","sources":["handbook.pdf"]}`

Pass the `sessionId` back in follow-up requests for conversation memory. Answers only use documents from the requested department.

### Streaming, history, config

```bash
curl -N -H "Content-Type: application/json" \
     -d '{"question":"...","department":"HR"}' http://localhost:8080/api/chat/stream   # SSE token stream
curl http://localhost:8080/api/chat/<sessionId>/history
curl http://localhost:8080/api/config          # retention settings + department list
```

### Evaluation

```bash
./eval/run-eval.sh   # replays eval/eval-set.json against /api/chat
```

## Roadmap (not yet implemented)

- Step 5: SSO / OAuth2 resource server + group-based retrieval filtering

See `DOCUMENTATION.md` for the full design and `COMMANDS.md` for the complete development command log.
