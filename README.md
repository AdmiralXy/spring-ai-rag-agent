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

These variables are read directly by Spring Boot:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/ai` | PostgreSQL connection URL |
| `DATABASE_USERNAME` | `aiuser` | PostgreSQL username |
| `DATABASE_PASSWORD` | `aipass` | PostgreSQL password |
| `EMBEDDING_DIMENSIONS` | `1536` | Vector dimension used in Liquibase migrations |
| `CONFIG_PATH` | `classpath:models` | Path to the folder containing `models.yaml` |

Use `.env.example` as a template for local variables. Spring Boot does not load `.env` automatically, so load it in your shell or IDE run configuration before starting the app.

### AI models settings

Chat models and embeddings are loaded from `models.yaml` in the directory pointed to by `CONFIG_PATH`.

Default bundled config:

- `src/main/resources/models/models.yaml`
- `src/main/resources/models/prompts/*.md`

Recommended local override:

- `CONFIG_PATH=file:./config/local`
- `config/local/models.yaml`
- `config/local/prompts/<model-name>.md` for optional custom system prompts

Minimal `models.yaml` example:

```yaml
embeddings:
  provider: openai-compatible
  baseUrl: https://api.openai.com
  apiKey: REPLACE_WITH_EMBEDDING_API_KEY
  model: text-embedding-3-small
  dimensions: 1536

models:
  - name: deepseek-chat
    displayName: DeepSeek V3.2
    alias: deepseek
    summarizer: true
    baseUrl: https://api.deepseek.com
    apiKey: REPLACE_WITH_CHAT_API_KEY
    properties:
      streaming: true
      temperature: 1.0
```

Important:

- `EMBEDDING_DIMENSIONS` in `.env` must match `embeddings.dimensions` in `models.yaml`
- API keys and model endpoints are configured in `models.yaml`, not via Spring environment variables

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/AdmiralXy/spring-ai-rag-agent.git
cd spring-ai-rag-agent
```

### 2. Prepare local config

1. Copy `.env.example` to `.env`
2. If you want to keep provider keys out of the classpath resources, create `config/local/models.yaml`
3. Set `CONFIG_PATH=file:./config/local` in `.env`

### 3. Start PostgreSQL

```bash
docker compose up -d
```

### 4. Run the application

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
