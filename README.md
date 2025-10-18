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
