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

## Repository Role

This repository is the **primary backend implementation** for KeepKind.

A separate frontend sandbox repository may exist for rapid UI iteration and prototyping, but this repository is the main source of truth for:

- backend APIs
- item and receipt lifecycle
- RAG pipeline
- embeddings and retrieval
- decision generation
- architecture and backend documentation

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
- CORS support for local frontend/backend development

## Tech Stack

- Java 17
- Spring Boot
- PostgreSQL
- pgvector
- Ollama
- Maven Wrapper

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
```
## Status

Backend foundation, RAG pipeline, and decision receipt lifecycle are implemented.
The backend is currently usable for:
1. item creation
2. source ingestion
3. embeddings retrieval
4. question answering with citations
5. receipt generation
6. receipt export
7. web-MVP decision artifact generation

## Local Development - Prerequisites
### Install:

1. Docker
2. Java 17+
3. Ollama

### You also need:
- PostgreSQL running locally or through Docker
- Ollama serving locally for embeddings and generation
- Run database (Postgres + pgvector)

```bash
docker compose -f infra/docker-compose.yml up -d
Start backend
cd server
./mvnw spring-boot:run
```

### Backend default URL:

http://localhost:8080
Run tests
```bash
cd server
./mvnw test
```

### Note: tests may require PostgreSQL to be running.

Check Ollama
```bash
curl http://localhost:11434/api/tags
```
RAG Example (Local)
1) Create item
```bash
curl -s -X POST http://localhost:8080/items \
  -H "Content-Type: application/json" \
  -d '{"name":"Coffee grinder","category":"appliance"}'
```
2) Add source (text)
```bash
curl -s -X POST http://localhost:8080/items/1/sources/text \
  -H "Content-Type: application/json" \
  -d '{"title":"Care note","text":"Replace burrs every 6-12 months depending on usage.","trustLevel":"high"}'
```
3) Embed chunks for that source
```bash
curl -s -X POST http://localhost:8080/sources/1/embed
```
4) Ask a question (returns answer + citations)
```bash
curl -sG "http://localhost:8080/items/1/ask" \
  --data-urlencode "q=How often should I replace the burrs?"
```


## Decision Receipts (Phase 3)
1. Create a receipt (stores in DB)
```bash
curl -s -X POST "http://localhost:8080/items/1/receipt?q=What%20should%20I%20do%20with%20this%20item%3F&k=5" | jq
```
2. List receipts (pagination + total + optional includeDeleted)
```bash
curl -s "http://localhost:8080/items/1/receipts?limit=10&offset=0" | jq
curl -s "http://localhost:8080/items/1/receipts?limit=10&offset=0&includeDeleted=true" | jq
```
3. Latest receipt
```bash
curl -s "http://localhost:8080/items/1/receipts/latest" | jq
```
4. Fetch receipt (item-scoped + global)
```bash
curl -s "http://localhost:8080/items/1/receipts/1" | jq
curl -s "http://localhost:8080/receipts/1" | jq
```
5. Soft delete a receipt
```bash
curl -s -X DELETE "http://localhost:8080/items/1/receipts/1" | jq
```
6. Export Markdown (global + item-scoped)
```bash
curl -OJ "http://localhost:8080/receipts/1/export.md"
curl -OJ "http://localhost:8080/items/1/receipts/1/export.md"
```


## Web MVP Decision Artifact Endpoint

#For the web frontend, the backend also supports a non-persisted decision artifact endpoint:

```bash
curl -s -X POST "http://localhost:8080/items/1/decision?q=What%20should%20I%20do%20with%20this%20item%3F&k=5" | jq
```

This endpoint returns a four-option decision artifact for web UI rendering and does not store the receipt in the database.

### Important Operational Notes:

- Maven wrapper is inside server/
- PostgreSQL must be running or backend startup/tests may fail
- Ollama must be running or decision generation may fail
- CORS is configured for local frontend/backend development
- The current frontend may seed item details as text sources before calling the decision endpoint

### Current Limitations:
- true image understanding is not yet handled by this backend
- current frontend may still use heuristic item detection
- citations exist in backend but may not yet be exposed as polished user-facing resources
- some web-MVP behavior currently relies on seeded text context

### Documentation:

```text
1. docs/architecture.md
2. docs/threat-model.md
3. docs/web-mvp-api.md
```
These documents describe the current architecture, risk model, and frontend/backend integration contract.

## Roadmap Direction:
### Planned future backend evolution includes:

1. real image understanding integration
2. richer structured decision intelligence
3. better resource grounding
4. item-type-specific intelligence
5. persistent user/item profiles
6. export and sharing improvements
7. stronger ownership lifecycle features

## Design Principle
### KeepKind Backend exists to power a structured decision product, not a chat interface.

The backend should continue supporting:
- clear decision artifacts
- explainable option generation
- future structured intelligence
- reliable integration with a calm, consumer-facing frontend
