# KeepKind Web MVP API (Contract)

Goal: a web app can (1) submit an item, (2) get a 4-option decision artifact with citations, and (3) store history locally (IndexedDB). Backend remains stateless for history.

## Base URL
- Local: http://localhost:8080

## MVP flow (web)

1) Create item (optional if you want to attach metadata)
2) Upload photo OR provide details
3) Backend returns ONE decision artifact containing all 4 plans + citations
4) Web stores artifact locally and lists it in the left sidebar

---

## Endpoint: Create item
POST /items

Request (example)
{
  "name": "iPhone 12",
  "category": "electronics",
  "condition": "cracked screen",
  "purchaseDate": "2022-01-10"
}

Response
{
  "id": 123
}

Notes:
- MVP can keep this minimal: name/category/condition only.
- Web can create item automatically after photo upload, or allow manual item creation.

---

## Endpoint: Upload photo (MVP v1)

POST /items/{itemId}/sources/photo

Request
- Content-Type: multipart/form-data
- Fields:
  - file: image (jpg/png)
  - title (optional): string
  - trustLevel (optional): "user" | "manufacturer" | "third_party"

Response
{
  "sourceId": 456,
  "itemId": 123,
  "stored": true
}

Notes:
- This endpoint stores the photo as a Source.
- In MVP v1, photo may be used only for item understanding (vision extraction); it is not chunked/embedded like text.

---

## Endpoint: Provide details (alternative to photo)

POST /items/{itemId}/sources/text

Request
{
  "title": "user-notes",
  "trustLevel": "user",
  "text": "Bought in 2022. Screen cracked. Battery drains fast. No water damage."
}

Response
{
  "sourceId": 789,
  "chunksCreated": 4
}

Notes:
- This is already implemented (text ingestion → chunking).

---

## Endpoint: Embed a source (for text sources)
POST /sources/{sourceId}/embed

Response
{
  "sourceId": 789,
  "embeddedChunks": 4,
  "embedModel": "nomic-embed-text:latest"
}

Notes:
- For MVP, web can call this automatically after adding text sources.
- Photo sources do not use this endpoint.

---

## Endpoint: Generate decision artifact (all 4 options + citations)
POST /items/{itemId}/decision?q=...&k=...

Response (contract)
{
  "itemId": 123,
  "summary": "iPhone 12 – cracked screen",
  "confidence": 0.82,

  "options": {
    "maintain": {
      "title": "Maintain",
      "steps": [
        "Wipe exterior weekly.",
        "Avoid moisture and heat."
      ],
      "whatChangesThis": [
        "If the screen damage spreads, switch to Repair."
      ]
    },
    "repair": {
      "title": "Repair",
      "steps": [
        "Back up data before service.",
        "Request an OEM-grade screen if available."
      ],
      "whatChangesThis": [
        "If repair cost exceeds resale value, consider Resell/Recycle."
      ]
    },
    "resell": {
      "title": "Resell",
      "steps": [
        "Factory reset after backup.",
        "Disclose screen condition clearly."
      ],
      "whatChangesThis": [
        "If buyer demand is low locally, consider trade-in."
      ]
    },
    "recycle": {
      "title": "Recycle",
      "steps": [
        "Remove SIM and wipe device.",
        "Use certified e-waste recycler."
      ],
      "whatChangesThis": [
        "If repair is affordable, prefer Repair before disposal."
      ]
    }
  },

  "citations": [
    { "chunkId": 1, "sourceId": 2, "distance": 0.59 }
  ],
  "assumptions": [
    "No liquid damage unless stated otherwise."
  ],

  "generation": {
    "chatModel": "llama3.2:3b",
    "embedModel": "nomic-embed-text:latest",
    "kUsed": 5,
    "promptVersion": "decision-v1"
  }
}

Notes:
- Web UI renders 4 tabs from `options`.
- Web stores the whole response in IndexedDB as the “receipt”.
- Backend should keep citations server-derived from retrieval.
- If evidence is insufficient, options should say so and assumptions must be explicit.

---

## Endpoint: Persist a chosen receipt (optional, Phase later)
POST /items/{itemId}/receipt?q=...&k=...

Notes:
- This already exists and persists server-side.
- Web MVP can skip server persistence and store locally only.

---

## Non-functional requirements (MVP)
- Timeouts: web should use 120s max for generation calls.
- CORS: allow localhost web dev origin.
- Deterministic shape: options always include 4 keys (maintain/repair/resell/recycle).

