# KeepKind Architecture (current)

## Purpose
KeepKind is a local-first service that helps users manage items they own and generate **decision receipts** (maintain/repair/resell/recycle/keep) grounded in user-provided sources, with **traceable citations** back to stored chunks.

## Core principles
- **Local-first by default:** data stored locally (Postgres). Models run locally (Ollama).
- **Evidence-first outputs:** answers/receipts are generated from retrieved context; if context is insufficient, the system should say so rather than guess.
- **Traceability:** every response includes chunk/source identifiers so results can be audited.

## High-level components
1) **Spring Boot API (Java 17)**
   - Handles item/source ingestion, chunking, embedding jobs, retrieval, RAG answering, and receipt generation.
2) **PostgreSQL + pgvector (Docker)**
   - Stores items, sources, chunks, embeddings, and receipts.
   - Provides vector similarity search over stored chunk embeddings.
3) **Ollama (local)**
   - **Embeddings model** for vectorization of chunk text + queries.
   - **Chat model** for generating answers/receipts from retrieved context.

## Data flow
### A) Ingest → Chunk
1. Client creates an item.
2. Client attaches a source (currently: text source).
3. Server computes a content hash and stores `sources`.
4. Server chunks the source text and stores each chunk in `chunks` (one row per chunk).

### B) Embed → Store vectors
1. Client triggers embedding for a given `sourceId`.
2. Server loads all chunks for that source.
3. Server calls Ollama embeddings API for each chunk.
4. Server stores embeddings into `chunks.embedding` (pgvector column).

### C) Retrieve → Answer (RAG)
1. Client asks a question for an item.
2. Server embeds the query with the embeddings model.
3. Server retrieves top-k chunks via pgvector similarity search.
4. Server calls Ollama chat with:
   - A strict “use only provided context” instruction
   - The retrieved chunks as context
5. Server returns:
   - `answer`
   - `citations` (chunkId/sourceId/chunkIndex/distance)
   - `contextUsed`

### D) Retrieve → Decision Receipt
1. Client requests a receipt for an item question.
2. Server retrieves top-k chunks (same as RAG).
3. Server calls Ollama chat with a strict receipt format:
   - `RECOMMENDATION: <maintain|repair|resell|recycle|keep>`
   - `RATIONALE: ...`
   - `ASSUMPTIONS: ...`
4. Server parses the model output and stores a row in `receipts` with:
   - question, recommendation, rationale
   - citations (JSONB)
   - assumptions (JSONB)
5. Server returns the saved receipt + citations.

## Storage model (conceptual)
- `items`: item metadata
- `sources`: source metadata (type, uri/title, trust level, content hash)
- `chunks`: chunked content + metadata + `embedding` (pgvector)
- `receipts`: persisted decision receipts + citations + assumptions

## API contracts (current)
- Health:
  - `GET /health/db`
- Items:
  - `POST /items`
- Sources:
  - `POST /items/{itemId}/sources/text`
- Chunk retrieval:
  - `GET /items/{itemId}/chunks/search?q=...` (keyword sanity check)
  - `GET /items/{itemId}/vector/search?q=...&k=...` (vector retrieval)
- Embedding job:
  - `POST /sources/{sourceId}/embed`
- RAG:
  - `GET /items/{itemId}/ask?q=...&k=...` (answer + citations)
- Receipts:
  - `POST /items/{itemId}/receipt?q=...&k=...` (persisted receipt)

## Response intent & format rules (KeepKind behavior)
- **Intent:** provide actionable guidance while remaining grounded in available sources.
- **Rules:**
  - Use only retrieved chunk context for answers/receipts.
  - If context is insufficient, explicitly say so.
  - Always include citations (chunk/source identifiers) in API responses.
  - Keep responses concise; avoid speculation.

## Repo layout
- `infra/` — docker compose for Postgres + pgvector; env example
- `server/` — Spring Boot application
- `README.md` — local dev + minimal usage examples

## Current milestone status
- Completed: ingestion → chunking → embeddings → vector retrieval → RAG answers → decision receipt generation + persistence.
- Next (Phase 3): receipt listing/retrieval endpoints and receipt export (Markdown/PDF), plus stronger citation persistence/formatting.
