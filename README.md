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
