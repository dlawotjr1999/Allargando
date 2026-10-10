# Allargando Backend

Spring Boot 3.5 (Java 21) + PostgreSQL 16 + Flyway. 코드·상세 규칙은 [`../CLAUDE.md`](../CLAUDE.md) 참고.

## 시작하기

### 1. 로컬 DB 실행

```bash
cd allargando
docker compose up -d
```

PostgreSQL 컨테이너(포트 5432, DB `allargando`)를 띄운다.

### 2. 로컬 설정 파일 준비

```bash
cd allargando/src/main/resources
cp application-local.properties.example application-local.properties
```

`application-local.properties`는 `.gitignore` 대상 — 실값(DB 비밀번호, KOPIS 서비스키 등)을 여기 채운다.

### 3. Firebase 서비스 계정 키 배치

Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성 후, 다운로드한 JSON을
`allargando/src/main/resources/firebase-service-account.json`으로 저장한다 (`.gitignore` 대상).

### 4. 필요한 시크릿

| 항목 | 용도 | 획득 경로 |
| --- | --- | --- |
| `DB_PASSWORD` | PostgreSQL 접속 | 로컬은 `docker-compose.yml` 값, 운영은 별도 발급 |
| `firebase-service-account.json` | Firebase Admin SDK (토큰 검증) | Firebase 콘솔 |
| `KOPIS_SERVICE_KEY` | 연주회 정보 동기화(KOPIS 공공 오픈API) | [공공데이터포털](https://www.data.go.kr) 신청 |

기본값이 없는 값(`DB_PASSWORD`, `KOPIS_SERVICE_KEY`)은 누락 시 애플리케이션이 **부팅 시점에 즉시 실패**하도록 설계돼 있다 — 운영 중 조용히 잘못된 상태로 뜨는 것을 방지.

### 5. 실행

```bash
cd allargando
./gradlew bootRun
```

기본 포트는 `8080`. 프론트엔드 로컬 실행 시 `EXPO_PUBLIC_API_URL`을 이 서버 주소로 맞춘다([`../Frontend/Allargando/README.md`](../Frontend/Allargando/README.md) 참고).

## 운영 환경변수

`application.properties`의 기본값은 운영 기준(안전한 쪽)이다. 운영에서 지정하는 값은 아래와 같다.

| 변수 | 필수 | 내용 |
| --- | --- | --- |
| `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` | 예 | PostgreSQL 접속. `DB_PASSWORD`는 기본값이 없다 |
| `KOPIS_SERVICE_KEY` | 예 | 기본값 없음(누락 시 부팅 실패) |
| `FIREBASE_CREDENTIALS_LOCATION` | 예 | `file:/경로` — 서비스 계정 키는 이미지에 넣지 않고 컨테이너 밖에서 마운트한다 |
| `FORWARD_HEADERS_STRATEGY` | 프록시 뒤면 예 | `native`. 없으면 서버가 보는 IP가 전부 프록시 주소라 **IP 기준 호출 제한(가입 시간당 10회, 닉네임 확인 분당 30회)이 모든 사용자에게 공유된다.** 프록시 없이 직접 노출할 때는 지정하지 않는다(헤더 위조 가능) |
| `CORS_ALLOWED_ORIGINS` | 아니오 | 비우면 전 오리진 차단. 모바일 앱만 쓰면 비워 둔다 |
| `SWAGGER_ENABLED` · `KOPIS_SYNC_MANUAL_TRIGGER_ENABLED` | 아니오 | 기본 `false`. 운영에서 켜지 않는다 |
| `SLOW_QUERY_LOG_MS` | 아니오 | 기본 `200`. 이보다 오래 걸린 SQL을 `org.hibernate.SQL_SLOW`로 남긴다(값이 아니라 `?`로 찍힌다). `0`이면 끈다 |

## 운영 배포

서버는 1대 전제다(동기화 실행 가드, 호출 제한 카운터, 알림 대기열이 인스턴스 메모리에 있다).

**이미지** — 버전 태그를 붙여 레지스트리에 올린다. `latest`만 쓰면 되돌릴 대상이 없다.

```bash
docker build -t allargando-backend backend/allargando
docker tag allargando-backend:latest <레지스트리>/allargando-backend:v2   # 배포마다 v1, v2, …
docker push <레지스트리>/allargando-backend:v2
```

이미지 빌드는 테스트를 건너뛴다(`-x test`). **CI의 `test` 잡이 통과한 커밋에서만 빌드한다.**

**실행** — 재시작 정책, 메모리 한도, 종료 대기 시간을 함께 준다.

```bash
docker run -d --name allargando --restart unless-stopped   --memory <한도> --stop-timeout 40   --env-file /opt/allargando/app.env   -v /opt/allargando/firebase-service-account.json:/secrets/firebase-service-account.json:ro   -p 127.0.0.1:8080:8080   <레지스트리>/allargando-backend:v2
```

- `--memory`: 이미지의 `-XX:MaxRAMPercentage=75`는 이 한도를 기준으로 힙을 잡는다. 없으면 호스트 메모리의 75%를 쓴다.
- `--stop-timeout 40`: 종료 때 처리 중인 요청(최대 30초)과 알림 발송(10초)을 마칠 시간이다. Docker 기본값 10초로는 모자란다.
- `127.0.0.1`에만 열고 앞단 리버스 프록시(HTTPS, 요청 본문 크기 상한)가 받는다.

**확인** — `GET /actuator/health`가 `UP`인지, 기동 로그에 Flyway 오류가 없는지 본다.

**되돌리기** — 이전 태그로 다시 띄운다(`docker stop allargando && docker rm allargando` 뒤 위 명령을 이전 태그로). 적용된 Flyway 마이그레이션은 되돌려지지 않으므로 스키마를 바꾸는 배포 전에는 DB 스냅샷을 만든다.

**마이그레이션 규칙**
- 적용된 마이그레이션 파일은 고치지 않는다(주석 한 글자도 체크섬이 달라져 부팅이 실패한다). 새 버전 파일만 추가한다.
- **첫 배포 이후에는 `NOT NULL` 컬럼을 한 번에 추가하지 않는다.** 옛 이미지로 되돌리면 그 컬럼을 채우지 못해 INSERT가 실패한다. 먼저 nullable(또는 기본값)로 추가해 배포하고, 다음 배포에서 `NOT NULL`로 바꾼다. 컬럼·테이블 삭제도 같은 순서다(코드가 먼저 안 쓰게 배포 → 다음 배포에서 삭제).
- V10(`user.name`·`terms_agreed_at`)은 DB가 비어 있는 첫 배포를 전제로 `NOT NULL`로 넣었다. 행이 있는 DB에는 적용되지 않는다.

## 테스트

```bash
cd allargando
./gradlew test
```

- Service는 Mockito 단위 테스트, Controller는 `@WebMvcTest` + `MockMvc`로 격리 실행 — 로컬 DB 없이도 대부분 통과한다.
- `@DataJpaTest` 기반 Specification 테스트는 내장 H2로 동작.

## API 문서

서버 실행 후 `http://localhost:8080/swagger-ui/index.html`에서 확인. 우측 상단 Authorize에 `Bearer <Firebase ID Token>`을 등록하면 인증이 필요한 엔드포인트도 바로 호출해볼 수 있다.

## CI

`.github/workflows/backend.yml` — `backend/**` 변경 시 GitHub Actions에서 PostgreSQL 서비스 컨테이너를 띄워 `./gradlew test` 실행.
