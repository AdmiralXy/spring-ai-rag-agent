# Spring AI RAG Agent

Spring Boot 3.5+ application featuring Retrieval-Augmented Generation (RAG) with UI, chat, and vector search, supporting OpenAI API-compatible models.

<p align="center">
  <a href="https://github.com/AdmiralXy/spring-ai-rag-agent">
    <img src="https://img.shields.io/badge/Backend-8A2BE2?style=for-the-badge">
  </a>
  <a href="https://github.com/AdmiralXy/spring-ai-rag-agent-ui">
    <img src="https://img.shields.io/badge/UI-8A2BE2?style=for-the-badge">
  </a>
  <a href="https://github.com/AdmiralXy/spring-ai-rag-agent-docker">
    <img src="https://img.shields.io/badge/Launch%20in%20Docker-FE7D37?style=for-the-badge">
  </a>
</p>

## ⚙️ Tech Stack

- Java 25+, Spring Boot, Spring Data JPA
- Spring AI (OpenAI-compatible / Ollama)
- PostgreSQL + pgvector
- Liquibase for DB migrations
- Docker Compose for local setup

## 🚀 Quick Start

### 1. Clone the repo
```bash
git clone https://github.com/AdmiralXy/spring-ai-rag-agent.git
cd spring-ai-rag-agent
```

### 2. Start dependencies (Postgres + Ollama)
```bash
docker compose up -d
```

### 3. Run the app
```bash
./gradlew bootRun
```
Default base path:  
`http://localhost:8080/api/agent`

Swagger:
`http://localhost:8080/api/agent/swagger-ui/index.html`

## 🔑 Configuration

Environment variables (see `application.yaml`):
```yaml
DATABASE_URL=jdbc:postgresql://localhost:5432/ai
DATABASE_USERNAME=aiuser
DATABASE_PASSWORD=aipass

AI_URL=https://api.deepseek.com
AI_KEY=your_api_key
AI_MODEL=deepseek-chat

OLLAMA_URL=http://localhost:11434
OLLAMA_EMBEDDING_MODEL=embeddinggemma:300m
```

## 📡 REST API

### Chats `/chats`
- `GET /chats` — list chats
- `POST /chats` — create chat (`{ "ragSpace": "space1" }`)
- `GET /chats/{id}/stream?text=...` — stream model response (SSE)
- `GET /chats/{id}/history` — get chat history
- `DELETE /chats/{id}` — delete chat

### Spaces `/spaces`
- `GET /spaces` — list spaces
- `POST /spaces` — create space (`{ "name": "space1" }`)
- `DELETE /spaces/{id}` — delete space

### RAG `/rag/{space}`
- `POST /rag/{space}/documents` — add document (`{ "text": "..." }`)
- `GET /rag/{space}/documents` — list documents
- `GET /rag/{space}/search?q=...&k=5` — semantic search
- `DELETE /rag/{space}/documents/{docId}` — delete document
