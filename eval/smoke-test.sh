#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
K="${K:-5}"

echo "Running KeepKind smoke test..."

# Sanity: DB health
curl -sS --fail --max-time 5 "$BASE_URL/health/db" | jq -e '.ok == true' >/dev/null

# Create an item
ITEM_JSON=$(curl -sS --fail --max-time 10 -X POST "$BASE_URL/items" \
  -H "Content-Type: application/json" \
  -d '{"name":"Smoke Test Item","category":"test","condition":"used"}')

ITEM_ID=$(echo "$ITEM_JSON" | jq -r '.id')
test -n "$ITEM_ID" && test "$ITEM_ID" != "null"

# Attach a text source (ingest + chunk)
SOURCE_TEXT="Wipe exterior weekly. Replace burrs every 6-12 months depending on usage. If broken, consider repair before replacement."
SOURCE_JSON=$(curl -sS --fail --max-time 10 -X POST "$BASE_URL/items/$ITEM_ID/sources/text" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg t "$SOURCE_TEXT" '{text:$t, title:"smoke-test-source", trustLevel:"user"}')")

SOURCE_ID=$(echo "$SOURCE_JSON" | jq -r '.sourceId // .source_id // .id')
test -n "$SOURCE_ID" && test "$SOURCE_ID" != "null"

# Embed the source chunks
curl -sS --fail --max-time 120 -X POST "$BASE_URL/sources/$SOURCE_ID/embed" | jq -e '.' >/dev/null

# Vector search should return at least 1 result
VEC=$(curl -sS --fail --max-time 30 "$BASE_URL/items/$ITEM_ID/vector/search?q=maintenance&k=$K")
echo "$VEC" | jq -e 'length > 0' >/dev/null

# Ask (RAG)
ASK=$(curl -sS --fail --max-time 120 "$BASE_URL/items/$ITEM_ID/ask?q=What%20should%20I%20do%20with%20this%20item%3F&k=$K")
echo "$ASK" | jq -e '.answer' >/dev/null
echo "$ASK" | jq -e '.citations | length > 0' >/dev/null

# Create receipt
RECEIPT=$(curl -sS --fail --max-time 120 -X POST "$BASE_URL/items/$ITEM_ID/receipt?q=What%20should%20I%20do%20with%20this%20item%3F&k=$K")
echo "$RECEIPT" | jq -e '.recommendation' >/dev/null
echo "$RECEIPT" | jq -e '.citations | length > 0' >/dev/null
echo "$RECEIPT" | jq -e '.assumptions' >/dev/null

echo "Smoke test passed."
