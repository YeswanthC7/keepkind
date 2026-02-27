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
