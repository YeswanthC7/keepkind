# KeepKind — Decision Receipts (MVP)

KeepKind is a decision-receipt generator: upload an item (photo) + add a few details and it generates four options as “receipts”:

- **Maintain**
- **Repair**
- **Resell**
- **Recycle**

The goal is to help decide what to do with items you own, with **citations to supporting sources**.

## Current MVP scope (what it does today)

- **Web UI (Next.js)**: a ChatGPT-style “receipt list” sidebar + a receipt viewer with tabs for Maintain/Repair/Resell/Recycle.
- **Item details form**: item type + optional brand/model/year/condition/issue/zip.
- **ZIP locality lookup (best-effort)**: shows locality/region based on ZIP so the UI doesn’t guess the state.
- **Backend (Spring Boot)**:
  - Create item: `POST /items`
  - Attach text source for the item: `POST /items/{itemId}/sources/text`
  - Embed the source (pgvector + Ollama embeddings): `POST /sources/{sourceId}/embed`
  - Generate decision artifact (4 options): `POST /items/{itemId}/decision`

**Important limitation (MVP):** the uploaded photo is currently used as an upload trigger. The backend is seeded with the user-entered details as a text source so retrieval isn’t empty. Actual image understanding (object detection / damage detection) is a later phase.

## What we have achieved so far

- End-to-end flow works locally:
  1) create an item in DB  
  2) attach a “seed” text source (user-entered details)  
  3) embed into pgvector via Ollama embedding model  
  4) generate a 4-option decision artifact and render in the UI
- Frontend renders a clean “decision receipt” with tabs per option.
- ZIP → locality lookup prevents incorrect “state” guesses in the receipt title.
- Item creation endpoint is stable (category defaults if missing).

## What is NOT done yet (roadmap)

### UX / Product
- Remove or hide the freeform **Question** field (use a default internal question).
- Auto-detect item type from photo (and show only relevant fields).
- Provide item-type-specific brand/model suggestions + **Other** fallback.

### Citations & Links (high priority)
- Replace raw citation IDs with **human-friendly citations**:
  - show source titles
  - include clickable resource links
- If the model mentions sites (Apple Support, Craigslist, etc.), show them as **small clickable buttons** within the option section.

### Backend data model
- Persist item metadata (type/brand/model/year/condition/zip) on the item, not only inside seeded text.
- Extend decision response to include structured “recommended links” per option, so the UI can render buttons without guessing.

## Architecture (high-level)

- **web/**: Next.js app (UI)
- **server/**: Spring Boot API
- **PostgreSQL + pgvector**: stores sources + chunks + embeddings
- **Ollama**: provides:
  - embeddings (`/api/embed`) for retrieval
  - chat generation for decision artifact

## Prerequisites

- Node.js (for the web app)
- Java + Maven (for Spring Boot)
- PostgreSQL running locally and reachable
- Ollama running locally at `http://localhost:11434`

You must have the required models available in Ollama (example):
- `nomic-embed-text:latest` (embeddings)
- `llama3.2:3b` (chat)

## Local development

### 1) Start dependencies

**PostgreSQL**
- Start your local Postgres (Docker or native). The server expects Postgres to be reachable (common default: `localhost:5432`).

**Ollama**
- Ensure Ollama is running:
  - `http://localhost:11434`

Check models:
```bash
curl http://localhost:11434/api/tags

2) Run the backend (Spring Boot)

The Maven wrapper is inside server/, so run commands from there:

cd server

./mvnw test
./mvnw spring-boot:run

Health checks:

curl http://localhost:8080/health
curl http://localhost:8080/health/db

If tests fail with connection refused to localhost:5432, Postgres is not running or config is incorrect.

3) Run the web app (Next.js)
cd web
npm install
npm run dev

Open:

http://localhost:3000

4) Configure API base (optional)

The web app uses:

NEXT_PUBLIC_API_BASE (defaults to http://localhost:8080)

Example:

export NEXT_PUBLIC_API_BASE=http://localhost:8080
How “Decision Receipts” are generated (MVP)

User uploads a file (photo) and enters a few details.

Web creates an item via POST /items.

Web seeds a text source with the user’s item details via POST /items/{itemId}/sources/text.

Web triggers embedding via POST /sources/{sourceId}/embed.

Web requests the decision artifact via POST /items/{itemId}/decision.

Notes on citations (current MVP)

The UI currently shows citations as internal identifiers (chunkId/sourceId/distance).

Converting these into real clickable resources (titles + URLs) is part of the next milestone.

Repo structure

web/ — Next.js UI

server/ — Spring Boot API

infra/ — local infra (docker-compose etc., if present)

docs/ — architecture + threat model docs

eval/ — smoke tests / eval scripts

Deploy (later)

This MVP is currently optimized for local dev. Production deployment (hosting Postgres, hosting Ollama or replacing with a hosted LLM provider, auth, rate limiting, etc.) is a later phase.