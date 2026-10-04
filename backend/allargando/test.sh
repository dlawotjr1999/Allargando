#!/bin/bash

# ── 설정 ──────────────────────────────────────────
# 실제 값은 이 파일에 커밋하지 않는다. cp .env.example .env 후 채우면 아래에서 자동으로 읽는다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/.env" ]; then
  source "$SCRIPT_DIR/.env"
fi

: "${FIREBASE_API_KEY:?FIREBASE_API_KEY가 없어요. backend/allargando/.env.example을 backend/allargando/.env로 복사해 값을 채워주세요.}"
EMAIL="${EMAIL:-test@test.com}"
PASSWORD="${PASSWORD:-test1234}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

# ── 서버 실행 확인 ────────────────────────────────
check_server() {
  curl -s --connect-timeout 3 $BASE_URL/api/users/check/test > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    echo "❌ 서버가 실행 중이지 않아요." >&2
    exit 1
  fi
  echo "✅ 서버 연결 확인"
}

# ── 토큰 자동 발급 ────────────────────────────────
get_token() {
  FIREBASE_RESPONSE=$(curl -s -X POST \
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"returnSecureToken\":true}")

  TOKEN_VALUE=$(echo "$FIREBASE_RESPONSE" | python -c \
    "import sys, json
data = json.load(sys.stdin)
print(data.get('idToken', ''))" 2>/dev/null)

  if [ -z "$TOKEN_VALUE" ]; then
    echo "❌ 토큰 발급 실패" >&2
    echo "Firebase 응답: $FIREBASE_RESPONSE" >&2
    exit 1
  fi

  echo "$TOKEN_VALUE"
}

# ── API 호출 공통 함수 ────────────────────────────
call_api() {
  local METHOD=$1
  local URL=$2
  local DATA=$3
  local AUTH=${4:-true}

  if [ "$AUTH" = "true" ]; then
    RESPONSE=$(curl -s -X $METHOD $BASE_URL$URL \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      ${DATA:+-d "$DATA"})
  else
    RESPONSE=$(curl -s -X $METHOD $BASE_URL$URL \
      -H "Content-Type: application/json" \
      ${DATA:+-d "$DATA"})
  fi

  STATUS=$(echo "$RESPONSE" | python -c \
    "import sys, json
try:
    print(json.load(sys.stdin).get('status', 'NO_STATUS'))
except:
    print('PARSE_ERROR')" 2>/dev/null)

  if [ "$STATUS" = "200" ]; then
    echo "✅ 성공 (status: $STATUS)"
    echo "$RESPONSE" | python -m json.tool
  else
    echo "❌ 실패 (status: $STATUS)"
    echo "$RESPONSE" | python -m json.tool
  fi
}

# ── 테스트 함수 ───────────────────────────────────

register() {
  echo -e "\n📌 register"
  call_api POST /api/auth/register '{
    "nickname": "testuser",
    "phoneNumber": "010-1234-5678",
    "instrument": "violin",
    "school": "music school",
    "isGraduate": false,
    "careers": [{"organization": "orchestra", "contexts": "2023 guest"}]
  }'
}

me() {
  echo -e "\n📌 get my info"
  call_api GET /api/users/me
}

# ── 실행 ──────────────────────────────────────────
check_server

TOKEN=$(get_token)
if [ -z "$TOKEN" ]; then
  echo "❌ 토큰이 비어있어요." >&2
  exit 1
fi
echo "✅ 토큰 발급 완료 (앞 20자: ${TOKEN:0:20}...)"

register
me