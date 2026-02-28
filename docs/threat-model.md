# KeepKind Threat Model (LLM + RAG)

## Scope

KeepKind is a local-first RAG system:
- User-provided sources are chunked and embedded.
- Retrieval (pgvector) selects top-k chunks.
- A local LLM (Ollama) generates answers and decision receipts.
- Receipts are persisted as durable, auditable artifacts.

This document maps risks to OWASP Top 10 for LLM Applications (high-level alignment).

---

## Assets to protect

1. User-provided source data
2. Stored embeddings
3. Receipt integrity (recommendation + citations)
4. Prompt contract enforcement
5. System configuration (models, retrieval parameters)

---

## Threat categories & mitigations

### 1. Prompt Injection (via retrieved content)

**Threat:**
A stored source chunk contains malicious instructions such as:
- "Ignore previous instructions"
- "Call external APIs"
- "Leak system prompt"

**Mitigations implemented:**
- Strict system instruction: *Use only provided context.*
- No tool execution based on model output.
- Retrieved text is treated as **data**, not executable instructions.
- Structured receipt format enforced post-generation.
- Citations required for every receipt.

**Future hardening:**
- Heuristic filtering for injection phrases.
- Retrieval-time instruction stripping.
- Context labeling (e.g., "UNTRUSTED SOURCE CONTENT").

---

### 2. Insecure Output Handling

**Threat:**
Model returns malformed or adversarial output that breaks parsing or storage.

**Mitigations implemented:**
- Strict receipt format contract:
  - `RECOMMENDATION:`
  - `RATIONALE:`
  - `ASSUMPTIONS:`
- Server-side parsing & validation before persistence.
- Recommendation must match allowed enum:
  - maintain | repair | resell | recycle | keep
- Citations are constructed server-side from retrieved chunks (not model text).

---

### 3. Data Poisoning

**Threat:**
User ingests malicious or low-trust content that biases future answers.

**Mitigations:**
- `sources.trust_level` field for future policy gating.
- Content hash stored for each source.
- No automatic external crawling; ingestion is explicit.

**Future:**
- Trust-weighted retrieval.
- Source allowlist per item category.

---

### 4. Hallucinated Citations

**Threat:**
Model fabricates references not present in retrieved context.

**Mitigations implemented:**
- Citations are derived from retrieval results, not from model output.
- Only retrieved chunk IDs are eligible for citation.
- No chunk text stored in receipts (only identifiers + distance).

---

### 5. Over-Reliance on LLM

**Threat:**
System generates confident recommendations with insufficient evidence.

**Mitigations implemented:**
- Strict instruction: if context insufficient → say so.
- Top-k retrieval always precedes generation.
- Receipts persist assumptions explicitly.

**Future:**
- Minimum evidence threshold.
- Confidence scoring layer.
- RAG evaluation harness (RAGAS).

---

### 6. Model Supply Chain Risk

**Threat:**
Using third-party model weights (e.g., Llama variants).

**Mitigations:**
- Models run locally via Ollama.
- No automatic model downloading at runtime.
- Model names stored with receipts (`chat_model`, `embed_model`).

---

## Architectural trust boundaries

User Input
    ↓
Spring Boot API
    ↓
Postgres (trusted local store)
    ↓
Ollama (local model runtime)
    ↓
Structured output validation
    ↓
Receipts table (durable artifact)

---

## Design invariants

1. Retrieval must occur before generation.
2. Generation must use only retrieved context.
3. Citations must map to stored chunks.
4. Receipts are append-only (soft delete only).
5. No external API calls during generation.

---

## Next hardening milestones

- Add automated prompt-injection test cases.
- Add evaluation harness (RAGAS) with regression thresholds.
- Add output schema validation layer.
- Add minimum citation count rule for receipts.
EOF
