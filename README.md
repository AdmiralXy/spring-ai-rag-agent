# Spring AI RAG Agent

Spring Boot 4 application with chat, Retrieval-Augmented Generation (RAG), and pgvector-based search for OpenAI-compatible providers.

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

## Tech Stack

- Java 25
- Spring Boot 4
- Spring AI
- PostgreSQL 17 + pgvector
- Liquibase
- Docker Compose

## Requirements

- JDK 25
- Docker and Docker Compose
- API key for at least one chat model
- API key for embeddings

## Configuration

### Environment variables

Use `.env.example` as a template for local variables. Spring Boot does not load `.env` automatically, so load it in your shell or IDE run configuration before starting the app.

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/AdmiralXy/spring-ai-rag-agent.git
cd spring-ai-rag-agent
```

### 2. Start PostgreSQL

```bash
docker compose up -d
```

### 3. Run the application

Gradle:

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

## Default URLs

- Base API: `http://localhost:8080/api/agent`
- Swagger UI: `http://localhost:8080/api/agent/swagger-ui/index.html`
