# KeepKind

Local-first app to help you manage items you own and generate “decision receipts”
(maintain / repair / resell / recycle) with evidence-backed citations.

## Goals
- Make ownership decisions easier and more sustainable
- Keep item history and sources in one place
- Export shareable decision receipts

## MVP (high level)
- Add an item (basic metadata)
- Attach sources (manuals/care/warranty/etc.)
- Ask questions with citations
- Generate a decision receipt (export)

## Tech (high level)
- Java / Spring Boot
- Local LLM (offline-capable)
- Postgres + vector search

## Status
Early development.

## License
TBD

## Local development

### Prereqs
- Docker
- Java 17+

### Run database (Postgres + pgvector)
```bash
docker compose -f infra/docker-compose.yml up -d
```

### RAG example (local)
```bash
# 1) Create item
curl -s -X POST http://localhost:8080/items \
  -H "Content-Type: application/json" \
  -d '{"name":"Coffee grinder","category":"appliance"}'

# 2) Add source (text)
curl -s -X POST http://localhost:8080/items/1/sources/text \
  -H "Content-Type: application/json" \
  -d '{"title":"Care note","text":"Replace burrs every 6-12 months depending on usage.","trustLevel":"high"}'

# 3) Embed chunks for that source
curl -s -X POST http://localhost:8080/sources/1/embed

# 4) Ask a question (returns answer + citations)
curl -sG "http://localhost:8080/items/1/ask" \
  --data-urlencode "q=How often should I replace the burrs?"

## Decision receipts (Phase 3)

```bash
# 1) Create a decision receipt (stores in DB)
curl -s -X POST "http://localhost:8080/items/1/receipt?q=What%20should%20I%20do%20with%20this%20item%3F&k=5" | jq

# 2) List receipts for an item
curl -s "http://localhost:8080/items/1/receipts" | jq

# 3) Fetch a receipt by id
curl -s "http://localhost:8080/receipts/1" | jq

# 4) Export receipt to Markdown (direct receipt route)
curl -OJ "http://localhost:8080/receipts/1/export.md"

# 5) Export receipt to Markdown (item-scoped route)
curl -OJ "http://localhost:8080/items/1/receipts/1/export.md"

Fetch a receipt for an item (item-scoped):
```bash
curl -s "http://localhost:8080/items/1/receipts/1" | jq

## Decision receipts (Phase 3)

```bash
# Create a receipt (stores in DB)
curl -s -X POST "http://localhost:8080/items/1/receipt?q=What%20should%20I%20do%20with%20this%20item%3F&k=5" | jq

# List receipts (pagination + total + optional includeDeleted)
curl -s "http://localhost:8080/items/1/receipts?limit=10&offset=0" | jq
curl -s "http://localhost:8080/items/1/receipts?limit=10&offset=0&includeDeleted=true" | jq

# Latest receipt
curl -s "http://localhost:8080/items/1/receipts/latest" | jq

# Fetch receipt (item-scoped + global)
curl -s "http://localhost:8080/items/1/receipts/1" | jq
curl -s "http://localhost:8080/receipts/1" | jq

# Soft delete a receipt
curl -s -X DELETE "http://localhost:8080/items/1/receipts/1" | jq

# Export Markdown (global + item-scoped)
curl -OJ "http://localhost:8080/receipts/1/export.md"
curl -OJ "http://localhost:8080/items/1/receipts/1/export.md"
