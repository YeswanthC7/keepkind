# KeepKind

KeepKind is a web-first decision engine that helps people decide what to do with the things they own.

Instead of generic advice, the app generates a **Decision Receipt** that evaluates four possible outcomes:

- Maintain
- Repair
- Resell
- Recycle

Each item becomes a receipt that explains the recommended steps and what factors could change the recommendation.

The goal is to help users make **financially smart and sustainable ownership decisions** in a calm, minimal interface.

---

# Product Concept

KeepKind is **not a chatbot**.

It is a **decision receipt generator**.

Users upload a photo of an item and provide a few details. The system then produces a structured decision artifact containing four parallel options.

Example outputs:

- Maintain the item
- Repair it
- Resell it
- Recycle it

Each option includes:

- steps to take
- factors that would change the recommendation
- helpful resource links

---

# Current MVP Features

The current MVP supports the following flow:

1. Upload an item photo
2. Automatically attempt to detect item type
3. Enter optional details
4. Generate a decision receipt
5. View four decision tabs
6. Access helpful external resources
7. Save receipts locally in the browser

The interface includes:

- ChatGPT-style sidebar history
- Receipt detail panel
- Four decision tabs
- Helpful action links

---

# Example User Flow

1. User uploads a photo of an item
2. KeepKind tries to infer the item type
3. User adds optional information
4. A **decision receipt** is generated
5. User compares four options:

Maintain | Repair | Resell | Recycle

Each option explains:

- what to do
- when the recommendation might change
- useful external resources

---

# Architecture

The system is split into two main components.

## Backend

Spring Boot API.

Responsibilities:

- item lifecycle
- ingestion of text sources
- chunking and embedding
- vector search retrieval
- decision generation
- receipt generation

Core technologies:

- Java 17
- Spring Boot
- PostgreSQL
- pgvector
- Ollama (local embeddings + chat models)

Backend phases implemented:

Phase 0 — bootstrap  
Phase 1 — ingestion and chunking  
Phase 2 — embeddings and retrieval  
Phase 3 — receipt lifecycle

---

## Frontend

Next.js web application.

Responsibilities:

- photo upload
- item metadata collection
- receipt display
- tab navigation
- resource links
- local receipt history

Core technologies:

- Next.js
- React
- TypeScript
- Tailwind CSS

Receipts are currently stored in **local browser storage**.

---

# Repository Structure
```bash
keepkind
│
├── docs
│ ├── architecture.md
│ ├── threat-model.md
│ └── web-mvp-api.md
│
├── eval
│ └── smoke-test.sh
│
├── infra
│ └── docker-compose.yml
│
├── server
│ └── Spring Boot backend
│
├── web
│ └── Next.js frontend
│
└── README.md
```
---

# Local Development

## Prerequisites

Install:

- Java 17
- Node.js 18+
- Docker
- Maven
- Ollama

---

# Start Infrastructure

Start Postgres:

```bash
docker compose up -d
Start Backend
cd server
./mvnw spring-boot:run
```

Backend runs on

http://localhost:8080
Start Frontend
```bash
cd web
npm install
npm run dev
```

Frontend runs on

http://localhost:3000
Ollama Requirement

Decision generation requires Ollama running locally.

Check:
```bash
curl http://localhost:11434/api/tags
```
Current MVP Limitations

The current version has several intentional simplifications.

Image understanding

The uploaded photo is not yet processed by an image model.

Item type detection currently relies on filename heuristics.

Future versions will integrate real vision models.

Citations

Citations currently reference internal chunk IDs.

Future versions will expose:

source titles

relevant excerpts

useful links

Local storage

Receipts are stored locally in the browser.

Future versions will support:

user accounts

persistent storage

cross-device history

Roadmap
Phase 4 — Web Product Layer

Focus on user experience.

Planned improvements:

real image understanding

automatic item detection

dynamic forms by item type

structured resource links

better receipt explanations

Phase 5 — Intelligence Layer

item-specific repair knowledge

resale price estimates

lifespan prediction

local repair network integration

Phase 6 — Platform

user accounts

shared receipts

exportable decision reports

sustainability insights

Design Philosophy

KeepKind aims to be:

calm

minimal

practical

non-judgmental

The app focuses on decision clarity, not chat interactions.

License

Internal development project.