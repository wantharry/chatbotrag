# Session Command Log — RAG Chatbot Build

Every command run during the session, in chronological order, grouped by phase.
Date: July 13, 2026

> Note: `<GROQ_API_KEY>` appears where the real key was used — it was always
> passed as an environment variable, never written into project files.

---

## Phase 0 — Environment discovery

```bash
# Check what's installed
java -version
mvn -version
docker --version
docker ps
ls -la /Users/vijjimuk/V_claude_code/Chatbot
# Result: Java, Maven, Docker all missing; empty project folder

# Locate Homebrew and CPU architecture
which brew            # → /opt/homebrew/bin/brew
uname -m              # → arm64 (Apple Silicon)
```

---

## Phase 1 — Project creation

```bash
# Create the source directory structure
mkdir -p /Users/vijjimuk/V_claude_code/Chatbot/src/main/resources \
         /Users/vijjimuk/V_claude_code/Chatbot/src/main/java/com/example/chatbot/{controller,service}
```

Files created (via editor, not shell):
- `pom.xml` — Spring Boot 3.4 + Spring AI 1.0 dependencies
- `src/main/resources/application.yml` — datasource, Groq, pgvector, embeddings config
- `src/main/java/com/example/chatbot/ChatbotApplication.java`
- `src/main/java/com/example/chatbot/service/IngestionService.java`
- `src/main/java/com/example/chatbot/service/RagService.java`
- `src/main/java/com/example/chatbot/controller/DocumentController.java`
- `src/main/java/com/example/chatbot/controller/ChatController.java`
- `docker-compose.yml`, `.gitignore`, `README.md`

---

## Phase 2 — Installing Java, Maven, Docker (all user-local, no sudo)

```bash
# Attempt 1: Homebrew — failed (permission-locked, would need sudo chown)
/opt/homebrew/bin/brew install openjdk@17 maven

# Attempt 2: SDKMAN — failed (requires bash 4+; macOS ships bash 3.2)
curl -s "https://get.sdkman.io?rcupdate=false" | bash
bash --version        # → 3.2.57

# Attempt 3 (worked): direct tarball downloads into ~/tools

# Java 17 (Temurin, Apple Silicon)
mkdir -p ~/tools && cd ~/tools
curl -sL -o jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
tar xzf jdk17.tar.gz && rm jdk17.tar.gz    # → jdk-17.0.19+10

# Maven (first tried 3.9.11 → 404; checked index, used 3.9.16)
curl -s https://dlcdn.apache.org/maven/maven-3/ | grep -o 'href="3[^"]*"'
curl -sL -o maven.tar.gz \
  "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz"
tar xzf maven.tar.gz && rm maven.tar.gz    # → apache-maven-3.9.16

# Verify
export JAVA_HOME=~/tools/jdk-17.0.19+10/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.16/bin:$PATH"
java -version         # → 17.0.19
mvn -version          # → 3.9.16

# Persist to shell profile
cat >> ~/.zshrc << 'EOF'
# Java + Maven (user-local installs)
export JAVA_HOME="$HOME/tools/jdk-17.0.19+10/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.16/bin:$PATH"
EOF

# Docker stack: Lima (VM) + Colima (runtime manager) + Docker CLI
# (Docker Desktop wasn't available at this point)
curl -sL https://api.github.com/repos/lima-vm/lima/releases/latest      # → v2.1.4
curl -sL https://api.github.com/repos/abiosoft/colima/releases/latest   # → v0.10.3

mkdir -p ~/tools/bin ~/tools/lima
cd ~/tools/lima
curl -sL "https://github.com/lima-vm/lima/releases/download/v2.1.4/lima-2.1.4-Darwin-arm64.tar.gz" | tar xz

cd ~/tools/bin
curl -sL -o colima "https://github.com/abiosoft/colima/releases/download/v0.10.3/colima-Darwin-arm64"
chmod +x colima
curl -sL "https://download.docker.com/mac/static/stable/aarch64/docker-28.3.2.tgz" \
  | tar xz --strip-components=1 docker/docker
chmod +x docker

# Persist PATH
cat >> ~/.zshrc << 'EOF'
export PATH="$HOME/tools/bin:$HOME/tools/lima/bin:$PATH"
EOF

# Verify
colima version        # → v0.10.3
limactl --version     # → 2.1.4
docker --version      # → 28.3.2

# Start the Colima VM (2 CPU, 4 GB RAM)
colima start --cpu 2 --memory 4
docker ps             # engine reachable
```

---

## Phase 3 — Database startup + first build

```bash
# docker compose plugin was missing from the static CLI; existing
# Docker Desktop symlinks in ~/.docker/cli-plugins provided it, but the
# credsStore entry broke image pulls — removed it (backup kept):
cp ~/.docker/config.json ~/.docker/config.json.bak
# (edited config.json to remove "credsStore": "desktop")

# Point CLI at Colima and start Postgres+pgvector
docker context use colima
cd /Users/vijjimuk/V_claude_code/Chatbot
docker compose up -d                  # pulls pgvector/pgvector:pg16, starts chatbot-postgres
docker ps                             # → chatbot-postgres: Up

# First compile
mvn -q compile                        # success
```

---

## Phase 4 — First run + end-to-end test

```bash
# Start the app (detached; log to /tmp/chatbot.log)
export GROQ_API_KEY=<GROQ_API_KEY>
cd /Users/vijjimuk/V_claude_code/Chatbot
mvn spring-boot:run > /tmp/chatbot.log 2>&1 &

# Wait for readiness, inspect startup log
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/chat \
  -X POST -H "Content-Type: application/json" -d '{"question":"ping"}'
tail -25 /tmp/chatbot.log
# Startup: ONNX model downloaded, pgvector schema created, Tomcat on 8080

# Create a test document and ingest it
cat > /tmp/company-handbook.txt << 'EOF'
Acme Corp Employee Handbook
Vacation Policy: ... 20 days ... carry over up to 5 days ...
Remote Work Policy: ... Expense Policy: ...
EOF
curl -F "file=@/tmp/company-handbook.txt" http://localhost:8080/api/documents/upload
# → {"chunksStored":1,"status":"ingested","filename":"company-handbook.txt"}

# Test RAG: in-context question
curl -H "Content-Type: application/json" \
     -d '{"question":"How many vacation days do I get, and how many can I carry over?"}' \
     http://localhost:8080/api/chat
# → correct answer with source citation

# Test grounding: out-of-context question
curl -H "Content-Type: application/json" \
     -d '{"question":"What is the dress code?"}' http://localhost:8080/api/chat
# → "I don't have that information in the uploaded documents."

# Cleanup
rm -f /tmp/company-handbook.txt
```

---

## Phase 5 — Adding the chat web UI

```bash
mkdir -p /Users/vijjimuk/V_claude_code/Chatbot/src/main/resources/static
# Created static/index.html (chat UI) via editor

# Restart the app to pick it up
lsof -ti :8080                        # → find app PID
kill <PID>
export GROQ_API_KEY=<GROQ_API_KEY>
mvn spring-boot:run > /tmp/chatbot.log 2>&1 &

# Verify
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/   # → 200
curl -H "Content-Type: application/json" \
     -d '{"question":"How many vacation days do I get?"}' http://localhost:8080/api/chat
```

---

## Phase 6 — Debugging the "Hera" retrieval failure

```bash
# What documents are stored?
docker exec chatbot-postgres psql -U chatbot -d chatbot \
  -c "SELECT metadata->>'filename' AS file, count(*) AS chunks,
      sum(length(content)) AS total_chars FROM vector_store GROUP BY 1;"
# → greek_mythology.pdf: 60 chunks, ~200 KB text

# Was ingestion logged? Any errors?
grep -i "ingested\|error\|exception" /tmp/chatbot.log | tail -10

# Does any chunk mention Hera? Does the book state her height?
docker exec chatbot-postgres psql -U chatbot -d chatbot \
  -c "SELECT count(*) FROM vector_store WHERE content ILIKE '%hera%';"     # → 11
docker exec chatbot-postgres psql -U chatbot -d chatbot -t -A \
  -c "SELECT regexp_replace(content, E'[\\n ]+', ' ', 'g') FROM vector_store
      WHERE content ILIKE '%hera%'" | grep -io '.\{120\}hera.\{160\}'
# → FOUND: "The wife of Zeus was the tall and beautiful goddess Hera."
# Conclusion: answer exists in DB but similarity search missed it —
# 800-token chunks exceeded the embedding model's ~512-token window,
# so chunk tails were never represented in the vectors.

# Fix applied in code (editor):
#   IngestionService: chunk size 800 → 300 tokens
#   RagService:       topK 5 → 8

# Purge the badly-chunked data and restart
docker exec chatbot-postgres psql -U chatbot -d chatbot \
  -c "DELETE FROM vector_store WHERE metadata->>'filename' = 'greek_mythology.pdf';"
lsof -ti :8080 && kill <PID>
export GROQ_API_KEY=<GROQ_API_KEY>
mvn spring-boot:run > /tmp/chatbot.log 2>&1 &

# Re-ingest and re-test
mdfind -name greek_mythology.pdf      # → ~/Downloads/greek_mythology.pdf
curl -F "file=@/Users/vijjimuk/Downloads/greek_mythology.pdf" \
     http://localhost:8080/api/documents/upload
# → 162 chunks (was 60)
curl -H "Content-Type: application/json" \
     -d '{"question":"Was Hera tall or short?"}' http://localhost:8080/api/chat
# → "Hera was described as 'the tall and beautiful goddess'." ✅
curl -H "Content-Type: application/json" \
     -d '{"question":"What bird did Hera choose as her favorite and why?"}' \
     http://localhost:8080/api/chat
# → peacock, correct ✅
```

---

## Phase 7 — Migration from Colima to Docker Desktop

```bash
# Confirm both engines exist as contexts
docker context ls
# → colima (active), default, desktop-linux (Docker Desktop, newly installed)

# Tear down on the Colima side
docker --context colima compose -f /Users/vijjimuk/V_claude_code/Chatbot/docker-compose.yml down
lsof -ti :5432                        # find what still holds the port
kill <app-java-PID>                   # stop the Spring Boot app too
colima stop                           # shut down the Colima VM
lsof -ti :8080                        # one leftover listener (VS Code helper) — killed

# Bring everything up on Docker Desktop
docker context use desktop-linux
cd /Users/vijjimuk/V_claude_code/Chatbot
docker compose up -d                  # fresh chatbot-postgres in Docker Desktop
docker ps                             # → Up

# Restart app and re-ingest (volumes don't transfer between VMs)
export GROQ_API_KEY=<GROQ_API_KEY>
mvn spring-boot:run > /tmp/chatbot.log 2>&1 &
curl -F "file=@/Users/vijjimuk/Downloads/greek_mythology.pdf" \
     http://localhost:8080/api/documents/upload          # → 162 chunks
curl -H "Content-Type: application/json" \
     -d '{"question":"Was Hera tall or short?"}' http://localhost:8080/api/chat   # ✅
docker exec chatbot-postgres psql -U chatbot -d chatbot \
  -c "SELECT metadata->>'filename', count(*) FROM vector_store GROUP BY 1;"
```

---

## Phase 8 — Version control: git init, commit, push to GitHub

```bash
# Initialize repo (folder wasn't under git)
cd /Users/vijjimuk/V_claude_code/Chatbot
git init                              # → branch main

# Repo-local identity (GitHub noreply email keeps real email private)
git config user.name "wantharry"
git config user.email "8313871+wantharry@users.noreply.github.com"

# Safety scan: make sure the API key isn't in any tracked file
grep -rn "gsk_" --include="*" . | grep -v GROQ_API_KEY   # → no secrets found

# Stage + initial commit (target/ excluded by .gitignore)
git add -A
git commit -m "Initial commit: RAG chatbot with Spring AI, pgvector, and Groq" \
           -m "<body>...Co-authored-by: Copilot ..."

# SSH setup for push (no key existed yet)
ssh-keyscan -t ed25519,rsa,ecdsa github.com >> ~/.ssh/known_hosts
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -N "" -C "wantharry@github"
cat ~/.ssh/id_ed25519.pub             # → added manually at github.com/settings/ssh/new
ssh -T git@github.com                 # → "Hi wantharry! You've successfully authenticated"

# Add remote and push
git remote add origin git@github.com:wantharry/chatbotrag.git
git push -u origin main               # → repo live at github.com/wantharry/chatbotrag
```

---

## Phase 9 — Upgrade to Spring Boot 4.1.0 + Spring AI 2.0.0

```bash
# Confirm what's actually released (spring.io claimed 4.1.x; verified on Maven Central)
curl -s "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml" \
  | grep -o '<latest>[^<]*</latest>'                     # → 4.1.0
curl -s "https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml" \
  | grep -o '<latest>[^<]*</latest>'                     # → 2.0.0

# pom.xml edited: Boot 3.4.5 → 4.1.0, Spring AI 1.0.0 → 2.0.0

# Build attempt 1 — FAILED:
mvn clean compile
# → "no suitable constructor found for TokenTextSplitter(int,int,int,int,boolean)"
# Fix (editor): migrated IngestionService to TokenTextSplitter.builder()

mvn clean compile                     # → success

# Run attempt — chat returned 500:
grep -A15 "ERROR" /tmp/chatbot.log
# → 404: Unknown request URL: POST /openai/chat/completions
# Cause: Spring AI 2.0 uses the official OpenAI SDK, which doesn't append /v1
# Fix (editor): application.yml base-url → https://api.groq.com/openai/v1

# Restart and verify end-to-end
kill <java-PID>
export GROQ_API_KEY=<GROQ_API_KEY>
mvn spring-boot:run > /tmp/chatbot.log 2>&1 &
curl -H "Content-Type: application/json" \
     -d '{"question":"Was Hera tall or short?"}' http://localhost:8080/api/chat   # ✅
printf 'The Zephyr 9000 blender has a 12-year warranty...' > /tmp/widget.txt
curl -F "file=@/tmp/widget.txt" http://localhost:8080/api/documents/upload         # ✅ fresh ingestion
curl -H "Content-Type: application/json" \
     -d '{"question":"How long is the Zephyr 9000 warranty?"}' http://localhost:8080/api/chat  # ✅
rm /tmp/widget.txt

# Commit + push the upgrade
git add -A
git commit -m "Upgrade to Spring Boot 4.1.0 and Spring AI 2.0.0" -m "<body>..."
git push                              # → 9a31003 on main
```

---

## Phase 10 — Step 6 production hardening (streaming, history, doc management, retention, eval)

```bash
# Commit-before-changes check
git status --short                    # clean tree

# (Code written: DocumentEntity/ChatSession/ChatMessage entities + repositories,
#  RetentionService, ConfigController, rewritten Ingestion/RagService,
#  new Chat/Document controller endpoints, streaming UI, eval harness)

mvn clean compile                     # ✅ BUILD SUCCESS

# First restart failed after 5.5 min:
#   ORT_INVALID_PROTOBUF — Failed to load model
# Diagnosis: Spring AI used a randomized temp dir per run, so the 90MB ONNX
# embedding model re-downloaded on EVERY restart; one interrupted download
# left a truncated model.onnx (89,632,583 vs 90,387,630 bytes).
du -sh $TMPDIR/spring-ai-model-cache*/*        # found 5 duplicate caches

# Fix: pin the cache dir in application.yml
#   spring.ai.transformers.embedding.cache.directory: ${user.home}/.cache/chatbot-onnx
mkdir -p ~/.cache/chatbot-onnx
cp -R $TMPDIR/spring-ai-model-cache<good-run>/* ~/.cache/chatbot-onnx/   # seed with intact copy
rm -rf $TMPDIR/spring-ai-model-cache* $TMPDIR/spring-ai-onnx-generative  # clean up

# Restart — now starts in ~9s instead of 5+ min
mvn spring-boot:run > /tmp/chatbot.log 2>&1 &

# Verify all new endpoints
curl http://localhost:8080/api/config                        # {"retentionDays":90}
curl http://localhost:8080/api/documents                     # [] (legacy chunks have no registry row)
curl -H "Content-Type: application/json" \
     -d '{"question":"Was Hera tall or short?"}' http://localhost:8080/api/chat
                                                             # ✅ answer + sessionId
curl -H "Content-Type: application/json" \
     -d '{"sessionId":"<uuid>","question":"Which bird was sacred to her?"}' \
     http://localhost:8080/api/chat                          # ✅ memory works
curl http://localhost:8080/api/chat/<uuid>/history           # ✅ all turns persisted
curl -N -H "Content-Type: application/json" \
     -d '{"question":"Who was Zeus?"}' http://localhost:8080/api/chat/stream
                                                             # ✅ SSE meta/token/done events

# Document lifecycle: upload → list → ask → delete → confirm chunks gone
printf 'The Atlantis Project code name is BLUE HERON...' > /tmp/atlantis.txt
curl -F "file=@/tmp/atlantis.txt" http://localhost:8080/api/documents/upload   # → id
curl http://localhost:8080/api/documents                                        # listed ✅
curl -H "Content-Type: application/json" \
     -d '{"question":"What is the Atlantis Project code name?"}' \
     http://localhost:8080/api/chat                          # "BLUE HERON" ✅
curl -X DELETE http://localhost:8080/api/documents/<id>      # deleted ✅
curl -H "Content-Type: application/json" \
     -d '{"question":"What is the Atlantis Project code name?"}' \
     http://localhost:8080/api/chat                          # refuses ✅ (chunks gone)

# Eval harness
./eval/run-eval.sh
# 2 passed, then Groq free tier hit its 100k tokens/day limit (429 → empty
# answers). Not a RAG failure — rerun after the daily window resets.
```

---

## Phase 11 — Add Google Gemini as switchable LLM provider

```bash
# Groq free tier hit its 100k tokens/day cap during Step 6 testing → added a
# second provider. Verified the Google AI Studio key against the
# OpenAI-compatible endpoint first:
curl -H "Authorization: Bearer $GOOGLE_API_KEY" -H "Content-Type: application/json" \
     -d '{"model":"gemini-2.5-flash","messages":[{"role":"user","content":"Say OK"}],"max_tokens":10}' \
     https://generativelanguage.googleapis.com/v1beta/openai/chat/completions   # ✅ 200

# Added a `gemini` Spring profile document to application.yml:
#   base-url: https://generativelanguage.googleapis.com/v1beta/openai
#   api-key: ${GOOGLE_API_KEY}
#   model: gemini-2.5-flash

# Restart on the gemini profile (embeddings are local — no re-ingestion needed)
kill <java-PID>
export GOOGLE_API_KEY=<GOOGLE_API_KEY>
mvn spring-boot:run -Dspring-boot.run.profiles=gemini > /tmp/chatbot.log 2>&1 &

# Verify — answered from the PDF via Gemini
curl -H "Content-Type: application/json" \
     -d '{"question":"Was Hera tall or short?"}' http://localhost:8080/api/chat  # ✅

# Switch back to Groq anytime: restart without the profile flag
```

---

## Quick reference — recurring commands

```bash
# Start everything (Docker Desktop must be running)
cd /Users/vijjimuk/V_claude_code/Chatbot
docker compose up -d
export GROQ_API_KEY=<GROQ_API_KEY>
mvn spring-boot:run

# Publish changes
git add -A && git commit -m "..." && git push

# Status checks
docker ps
tail -f /tmp/chatbot.log
lsof -ti :8080

# Inspect the vector DB
docker exec chatbot-postgres psql -U chatbot -d chatbot \
  -c "SELECT metadata->>'filename', count(*) FROM vector_store GROUP BY 1;"

# Upload / ask
curl -F "file=@/path/to/doc.pdf" http://localhost:8080/api/documents/upload
curl -H "Content-Type: application/json" \
     -d '{"question":"..."}' http://localhost:8080/api/chat

# Stop the app
kill $(lsof -ti :8080)
```
