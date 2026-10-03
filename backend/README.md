# Poco a Poco Backend

Spring Boot 3.5 (Java 21) + PostgreSQL 16 + Flyway. 코드·상세 규칙은 [`../CLAUDE.md`](../CLAUDE.md) 참고.

## 시작하기

### 1. 로컬 DB 실행

```bash
cd obri
docker compose up -d
```

PostgreSQL 컨테이너(포트 5432, DB `obri`)를 띄운다.

### 2. 로컬 설정 파일 준비

```bash
cd obri/src/main/resources
cp application-local.properties.example application-local.properties
```

`application-local.properties`는 `.gitignore` 대상 — 실값(DB 비밀번호, KOPIS 서비스키 등)을 여기 채운다.

### 3. Firebase 서비스 계정 키 배치

Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성 후, 다운로드한 JSON을
`obri/src/main/resources/firebase-service-account.json`으로 저장한다 (`.gitignore` 대상).

### 4. 필요한 시크릿

| 항목 | 용도 | 획득 경로 |
| --- | --- | --- |
| `DB_PASSWORD` | PostgreSQL 접속 | 로컬은 `docker-compose.yml` 값, 운영은 별도 발급 |
| `firebase-service-account.json` | Firebase Admin SDK (토큰 검증) | Firebase 콘솔 |
| `KOPIS_SERVICE_KEY` | 연주회 정보 동기화(KOPIS 공공 오픈API) | [공공데이터포털](https://www.data.go.kr) 신청 |

기본값이 없는 값(`DB_PASSWORD`, `KOPIS_SERVICE_KEY`)은 누락 시 애플리케이션이 **부팅 시점에 즉시 실패**하도록 설계돼 있다 — 운영 중 조용히 잘못된 상태로 뜨는 것을 방지.

### 5. 실행

```bash
cd obri
./gradlew bootRun
```

기본 포트는 `8080`. 프론트엔드 로컬 실행 시 `EXPO_PUBLIC_API_URL`을 이 서버 주소로 맞춘다([`../Frontend/ObriApp/README.md`](../Frontend/ObriApp/README.md) 참고).

## 테스트

```bash
cd obri
./gradlew test
```

- Service는 Mockito 단위 테스트, Controller는 `@WebMvcTest` + `MockMvc`로 격리 실행 — 로컬 DB 없이도 대부분 통과한다.
- `@DataJpaTest` 기반 Specification 테스트는 내장 H2로 동작.
- `test.sh` (bash+curl 스크립트)로 토큰 발급부터 전체 API 플로우를 수동 점검할 수 있다. Git Bash에서는 `python3` 대신 `python` 사용(버전 출력이 토큰에 섞이는 문제 회피).

## API 문서

서버 실행 후 `http://localhost:8080/swagger-ui/index.html`에서 확인. 우측 상단 Authorize에 `Bearer <Firebase ID Token>`을 등록하면 인증이 필요한 엔드포인트도 바로 호출해볼 수 있다.

## CI

`.github/workflows/backend.yml` — `backend/**` 변경 시 GitHub Actions에서 PostgreSQL 서비스 컨테이너를 띄워 `./gradlew test` 실행.
