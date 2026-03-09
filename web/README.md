# KeepKind Backend

KeepKind Backend is the primary backend and core source of truth for the KeepKind product.

KeepKind is a decision engine that helps users decide what to do with things they own by generating a structured **Decision Receipt**.

It is **not** a generic chatbot.

For each item, the system evaluates four parallel options:

- Maintain
- Repair
- Resell
- Recycle

This repository contains the backend services, retrieval pipeline, receipt generation logic, and supporting documentation for the product.

---

## Repository Role

This repository is the **primary backend implementation** for KeepKind.

A separate frontend sandbox repository may exist for rapid UI iteration and prototyping, but this repository is the main source of truth for:

- backend APIs
- item and receipt lifecycle
- RAG pipeline
- embeddings and retrieval
- decision generation
- architecture and backend documentation

---

## Product Summary

KeepKind helps users make thoughtful, financially intelligent, and sustainable ownership decisions.

Core flow:

1. User uploads an item photo in the frontend
2. User adds structured details
3. Backend builds decision context
4. System generates a **Decision Receipt**
5. Receipt presents four options:
   - Maintain
   - Repair
   - Resell
   - Recycle

The product is designed to feel calm, minimal, and practical.

---

## Current Backend Capabilities

### Phase 0 — Bootstrap
Implemented:

- Spring Boot skeleton
- PostgreSQL + pgvector local infra
- health endpoint

### Phase 1 — Ingestion and Indexing
Implemented:

- items / sources / chunks model
- source ingestion via text
- chunking pipeline

### Phase 2 — Embeddings, Retrieval, and Basic RAG
Implemented:

- Ollama embedding client
- embedding endpoint
- vector search
- ask endpoint with citations

### Phase 3 — Decision Receipts and Lifecycle
Implemented:

- receipt generation
- persisted receipts
- list / latest / read flows
- export flow
- soft delete
- receipt versioning
- generation metadata

### Web MVP Support
Implemented for frontend integration:

- item creation
- source seeding
- source embedding
- non-persisted `/decision` artifact endpoint for web receipt generation
- CORS support for local frontend development

---

## Key Backend Endpoints

Examples of supported backend capabilities include:

- item creation
- source ingestion
- embedding generation
- vector retrieval
- ask / RAG answer generation
- decision artifact generation
- persisted receipt lifecycle
- receipt export

The backend includes a dedicated decision endpoint for web receipt generation:

- `POST /items/{itemId}/decision?q=...&k=...`

This endpoint returns a four-option decision artifact used by the frontend.

---

## Tech Stack

- Java 17
- Spring Boot
- PostgreSQL
- pgvector
- Ollama
- Maven Wrapper

---

## Repository Structure

```text
server/
  HELP.md
  mvnw
  mvnw.cmd
  pom.xml
  src/main/java/com/keepkind/
    AskController.java
    ChunkSearchController.java
    ChunkService.java
    EmbeddingController.java
    EmbeddingTestController.java
    HealthController.java
    ItemController.java
    KeepkindServerApplication.java
    OllamaChatClient.java
    OllamaEmbeddingClient.java
    ReceiptController.java
    ReceiptReadController.java
    SourceController.java
    VectorSearchController.java
    CorsConfig.java
  src/main/resources/
    application.properties
    application.yml
  src/test/java/com/keepkind/
    KeepkindServerApplicationTests.java

docs/
  architecture.md
  threat-model.md
  web-mvp-api.md

eval/
  smoke-test.sh

infra/
  docker-compose.yml

Local Development
Prerequisites

Install:

Java 17

Docker

Ollama

You also need:

PostgreSQL running locally or through Docker

Ollama serving locally for embeddings and generation

Start Infrastructure

From the project root:

docker compose up -d
Start Backend

From the project root:

cd server
./mvnw spring-boot:run

Backend default URL:

http://localhost:8080
Run Tests
cd server
./mvnw test

Note:
tests may require PostgreSQL to be running.

Ollama Requirement

Decision generation and embeddings require Ollama to be available locally.

Check Ollama:

curl http://localhost:11434/api/tags
Important Operational Notes

Maven wrapper is inside server/

PostgreSQL must be running or backend startup/tests may fail

Ollama must be running or decision generation may fail

CORS is configured for local frontend/backend development

The current frontend may seed item details as text sources before calling the decision endpoint

Current Limitations

true image understanding is not yet handled by this backend

current frontend may still use heuristic item detection

citations exist in backend but may not yet be exposed as polished user-facing resources

some web-MVP behavior currently relies on seeded text context

Documentation

See:

docs/architecture.md

docs/threat-model.md

docs/web-mvp-api.md

These documents describe the current architecture, risk model, and frontend/backend integration contract.

Roadmap Direction

Planned future backend evolution includes:

real image understanding integration

richer structured decision intelligence

better resource grounding

item-type-specific intelligence

persistent user/item profiles

export and sharing improvements

stronger ownership lifecycle features

Design Principle

KeepKind Backend exists to power a structured decision product, not a chat interface.

The backend should continue supporting:

clear decision artifacts

explainable option generation

future structured intelligence

reliable integration with a calm, consumer-facing frontend

License

Internal development project.