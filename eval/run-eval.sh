#!/usr/bin/env bash
# Step 6.24 — Evaluation runner: replays eval-set.json against /api/chat and
# checks each answer for expected keywords. Run after ANY tuning change
# (chunk size, topK, prompt, model) to catch retrieval regressions.
#
# Usage: ./run-eval.sh [base_url]     (default http://localhost:8080)

set -u
BASE_URL="${1:-http://localhost:8080}"
EVAL_FILE="$(dirname "$0")/eval-set.json"
PASS=0
FAIL=0

count=$(python3 -c "import json; print(len(json.load(open('$EVAL_FILE'))))")

for i in $(seq 0 $((count - 1))); do
  question=$(python3 -c "import json; print(json.load(open('$EVAL_FILE'))[$i]['question'])")
  keywords=$(python3 -c "import json; print('|'.join(json.load(open('$EVAL_FILE'))[$i]['expect_keywords']))")

  answer=$(curl -s -H "Content-Type: application/json" \
    -d "{\"question\": \"$question\"}" "$BASE_URL/api/chat" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('answer',''))" 2>/dev/null)

  ok=true
  IFS='|' read -ra kws <<< "$keywords"
  for kw in "${kws[@]}"; do
    if ! echo "$answer" | grep -qi "$kw"; then
      ok=false
      break
    fi
  done

  if $ok; then
    PASS=$((PASS + 1))
    echo "✅ PASS: $question"
  else
    FAIL=$((FAIL + 1))
    echo "❌ FAIL: $question"
    echo "   expected keywords: ${keywords//|/, }"
    echo "   got: ${answer:0:200}"
  fi
done

echo
echo "Results: $PASS passed, $FAIL failed out of $count"
[ "$FAIL" -eq 0 ]
