# Poco a Poco

악기 취미생이 앙상블·버스킹 멤버를 **무보수로** 모집하는 서비스. Spring Boot 백엔드 + Expo(React Native) 프론트엔드 모노레포이며, 1인 개발로 기획부터 백엔드 설계, 인증 연동, 프론트 API 연동, CI 구축까지 전체 라이프사이클을 진행했다.

- 백엔드 실행: [`backend/README.md`](backend/README.md)
- 프론트엔드 실행: [`Frontend/ObriApp/README.md`](Frontend/ObriApp/README.md)
- 코드 규칙·아키텍처 상세: [`CLAUDE.md`](CLAUDE.md)

## 프로젝트 배경 — 왜 피벗했는가

원래는 음대생이 결혼식·행사 연주 아르바이트 구인글을 올리고 지원하는 **유료** 매칭 플랫폼 "Obri"로 기획·구현했다. 개발 도중 실제 운영 중인 동명의 서비스(obri.co.kr, 직업정보제공사업 신고 업체)와 이름·유료 구인구직 모델이 겹친다는 것을 확인했고, 콩쿠르 정보 노출에 쓰던 크롤링 대상 사이트의 이용약관(정보 재배포 금지 조항)도 함께 리스크로 잡혔다.

기존에 구현해 둔 자산(낙관적 락 기반 동시성 제어, Firebase 인증, 알림, Post/Application 도메인 구조)은 그대로 살릴 수 있는 방향을 찾아, 대상을 **음대생 → 악기 취미생 전반**으로 넓히고 핵심 기능을 **유료 구인구직 → 무보수 앙상블·버스킹 멤버 모집**으로 전환했다. 구체적으로:

- `Post`에서 출연료(`pay`) 필드 제거 — 유료 알선으로 보일 소지 자체를 없앰
- "구인" 계열 용어를 백엔드 메시지·프론트 문구 전체에서 "모집"으로 교체
- 콩쿠르 크롤러(contest.co.kr) → **KOPIS(공연예술통합전산망) 공식 오픈API** 기반 연주회 정보 조회로 교체(계속 대중음악·클래식 공연 알림을 준다는 앱 가치는 유지하면서 법적 리스크는 제거)
- 음대생 전용 프로필 필드(학교·졸업여부·학교이메일 인증) 제거

## 기술 스택

**Backend**
- Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security
- PostgreSQL 16 (로컬은 Docker Compose), Flyway (스키마 마이그레이션)
- Firebase Admin SDK (인증), Jsoup (KOPIS XML 파싱), springdoc-openapi (Swagger)
- JUnit5 + Mockito + AssertJ, H2(테스트 전용), GitHub Actions CI

**Frontend**
- Expo (React Native, React 19), expo-router (파일 기반 라우팅)
- Firebase JS SDK (Auth), TypeScript

**Infra / Tooling**
- Docker Compose(로컬 PostgreSQL), GitHub Actions(백엔드 CI), Gradle

## 아키텍처

### 도메인 구조
```
com.obri_back.obri
├── global        공통 응답 포맷 · 예외 처리 · Security 설정 · Firebase 설정
├── auth          Firebase 토큰 검증 · 회원가입/로그인
├── user          유저 · 경력(Career)
├── post          모집글 · 모집 악기(PostInstrument)
├── application   지원서 · 지원 상태 · 인가 정책
├── practice      연습일지
├── concert       연주회 정보(KOPIS 공공 오픈API 동기화)
└── notification  FCM 푸시 알림 이벤트/리스너
```
도메인 간 직접 의존을 지양하고, 마이페이지형 조회(`GET /api/posts/me`, `GET /api/applications/me`)는 User가 컬렉션을 갖는 대신 각 도메인이 소유자 기준 조회를 제공하도록 설계했다. User→Post/Application 역방향 의존을 제거하고 Post·Application이 User를 단방향 `@ManyToOne`으로 참조하는 방향으로 정리했다(양방향 연관은 User-Career, Post-PostInstrument로만 한정).

### 인증 — Firebase + 내부 PK 분리
- 비밀번호를 자체 저장하지 않고 Firebase Authentication에 위임. 내부 식별자(`id BIGINT`)와 외부 식별자(`firebase_uid`)를 분리해, 비즈니스 로직·FK는 항상 내부 id를 기준으로 삼는다.
- 요청 흐름: `Authorization: Bearer <ID Token>` → `FirebaseAuthFilter`(`OncePerRequestFilter`)가 토큰 검증 → `firebase_uid`로 User 조회 → `SecurityContext`에 저장 → 컨트롤러는 `@AuthenticationPrincipal User`로 사용.
- 전화번호는 자체 OTP 대신 **Firebase Phone Auth**로 검증하고, 검증된 ID Token의 `phone_number` claim만 신뢰(요청 바디의 평문 문자열은 신뢰하지 않음). DB에 UNIQUE 제약을 걸어 1인 1계정 판단의 앵커로 사용.

### 계층 간 데이터 원칙
- Service는 URL에서 온 리소스(Post, Application)는 ID로 받아 내부에서 조회하고, 필터가 이미 인증 과정에서 조회해 둔 User는 엔티티로 받는다.
- 단, 컨트롤러에서 넘어온 User는 필터가 조회한 **detached 인스턴스**이므로, 쓰기 서비스는 진입 직후 `findById(user.getId())`로 **managed 인스턴스를 재조회**한 뒤 그 인스턴스를 변경한다. 재조회 없이 detached 엔티티를 그대로 고치면 dirty checking 대상이 아니어서 커밋해도 DB에 조용히 반영되지 않는 문제가 있어(예외조차 발생하지 않음), `PostService`·`UserService`·`AuthService`의 모든 쓰기 메서드에 이 패턴을 일관 적용했다.

### 인가 — Policy 컴포넌트로 분리
`ApplicationAccessPolicy` 같은 전담 Policy 컴포넌트가 "누가 할 수 있는가"만 판단하고, 실제 판단 로직(`post.isOwnedBy(user)`, `application.isRecruiter(user)` 등)은 Tell-Don't-Ask 원칙에 따라 엔티티에 위임했다. 서비스나 정책 클래스가 `getUser().getId().equals(...)` 같은 다단 체인을 직접 다루지 않도록 캡슐화한 것이 포인트. 상태 변경(수락/거절/취소/철회)도 의도별 엔드포인트(`PATCH /api/applications/{id}/{accept|reject|cancel|revoke}`)로 나눠 인가를 라우트 단위로 고정했다.

### 알림 — 트랜잭션 커밋 이후 발송
도메인 서비스는 `NotificationService`를 직접 호출하지 않고 `ApplicationEventPublisher.publishEvent()`로 이벤트만 발행한다. `NotificationEventListener`가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 커밋 후에만 실제 FCM 발송을 수행하도록 분리해, 트랜잭션이 롤백되면 이벤트 자체가 버려져 "존재하지 않는 리소스에 대한 유령 알림"이 나가지 않도록 했다. 이벤트는 엔티티가 아닌 원시값(id, 토큰 등)만 담아 detach 문제와도 무관하게 만들었다.

### 동시성 제어
- 중복 지원 방지: `(post_id, user_id)` UNIQUE 제약 + `existsByPostIdAndUserId()` 사전 체크로 `409 Conflict` 응답.
- 낙관적 락(`@Version`)을 `PostInstrument`, `Application`에 적용해 동시 수정 충돌을 감지하고, `GlobalExceptionHandler`에서 `ObjectOptimisticLockingFailureException`을 409로 매핑.

### 공통 응답 · 예외 처리
모든 API가 `{ status, message, data }` 포맷(`APIResponse<T>`)을 따르며, `@RestControllerAdvice` 기반 `GlobalExceptionHandler`가 도메인 예외(`NotFoundException`, `ForbiddenException`, `ConflictException` 등)부터 프레임워크 예외(`DataIntegrityViolationException`, `MethodArgumentNotValidException`, `HttpMessageNotReadableException` 등)까지 하나의 포맷으로 변환한다. 중복 체크처럼 반복되는 로직은 `ConflictGuard.requireUnique(...)` 유틸로 모아 UNIQUE 필드별 분기 코드를 없앴다.

### 연주회 정보 — KOPIS 공공 오픈API 동기화
스케줄러가 KOPIS(공연예술통합전산망) 오픈API를 주기 호출해 연주회 정보를 upsert한다. 역할을 Client(HTTP)·Parser(XML 파싱, 네트워크 없이 단위테스트 가능)·Service(동기화·중복 방지)·Scheduler로 분리했고, 스케줄러와 수동 트리거 엔드포인트가 동기화 로직을 공유하므로 `AtomicBoolean`으로 동시 실행을 차단했다.

## 잘한 점 / 설계 결정

- **인증 식별자와 비즈니스 식별자 분리**: Firebase UID(외부)와 내부 PK를 분리해, 향후 인증 공급자가 바뀌어도 FK·비즈니스 로직이 영향받지 않도록 설계.
- **Tell-Don't-Ask 인가 모델**: 인가 판단을 Policy 컴포넌트 + 엔티티 메서드로 캡슐화해 "누가 무엇을 할 수 있는가"가 서비스 코드 곳곳에 흩어지지 않게 함.
- **이벤트 기반 알림으로 트랜잭션 경계와 발송 시점 분리**: `AFTER_COMMIT` 리스너 패턴으로 롤백 시 유령 알림이 나가는 사고를 구조적으로 차단.
- **DTO 오버로딩으로 N+1 방지 경로를 명시적으로 분기**: 단건 조회는 지연 로딩을 그대로 쓰고, 목록 조회는 배치 조회 결과를 주입하는 별도 팩토리 메서드(`from(application, user, careers)`)를 둬서, 호출부가 실수로 N+1 경로를 타는 걸 타입 시그니처 수준에서 방지.
- **CI에서 Flyway와 Hibernate 역할을 명확히 분리**: 운영/CI는 Flyway가 스키마를 만들고 Hibernate는 `ddl-auto=validate`로 엔티티 매핑과의 일치만 검증하도록 해, 마이그레이션 자체가 검증되지 않는 구멍을 없앰.
- **법적 리스크를 코드 설계로 흡수**: 사업모델 충돌이 발견된 뒤 기존 도메인 구조(Post/Application의 상태 전이·동시성 제어)는 그대로 두고 필드·용어·외부 연동만 갈아끼우는 방식으로 피벗해, 재작성 비용을 최소화.
- **프론트 API 계층에서 멱등성을 명시**: `api/*.ts`의 각 함수에 실제 백엔드 검증 로직(예: PENDING 상태에서만 허용되는 수락/거절)을 근거로 멱등 여부를 주석으로 남겨, 연속 탭 방지 같은 화면단 방어가 필요한 지점을 코드 레벨에서 드러냄.

## 트러블슈팅

### 1. 회원가입 보상 트랜잭션이 실행되지 않던 버그
**증상**: MySQL 저장이 UNIQUE 제약 위반으로 실패해도 Firebase 계정 롤백(`firebaseAuth.deleteUser()`)이 실행되지 않아 고아 Firebase 계정이 남음.
**원인**: `userRepository.save()`의 flush가 트랜잭션 커밋 시점(메서드 반환 이후)까지 지연되기 때문에, UNIQUE 위반 예외가 애초에 작성한 `try-catch` 블록 **밖에서** 터짐.
**해결**: `saveAndFlush()`로 바꿔 flush를 즉시 트리거해 예외를 catch 블록 안에서 잡히게 하고, 롤백 자체가 실패하는 경우도 `log.error`로 남기도록 보강. 회귀 방지를 위해 "저장 실패 시 Firebase 롤백 호출 검증", "롤백까지 실패해도 원래 예외로 던져지는지 검증" 두 개의 유닛 테스트를 추가.

### 2. 지원자 목록 조회 N+1
**증상**: 지원자 목록/내 지원 목록 조회 시 지원자마다 `user.getCareers()` 지연 로딩이 개별 쿼리로 나가 페이지 크기만큼 쿼리가 증가.
**해결**: 페이지 안의 지원자 id를 모아 `CareerRepository.findByUserIdIn()`으로 배치 조회한 뒤 `Map<userId, List<CareerDTO>>`로 그룹화해 DTO 변환 시 주입. 다른 도메인이 `CareerRepository`를 직접 찌르지 않도록 배치 조회 메서드를 `UserService`에 두어 도메인 경계를 유지.

### 3. 악기명 중복 등록 시 예외
**증상**: 모집글에 같은 악기명을 중복으로 등록하고 이후 글을 수정하면 `replaceInstruments`(이름을 키로 기존/신규 악기를 병합하는 로직)가 `Collectors.toMap`의 기본 동작(키 중복 시 `IllegalStateException`)에 걸려 500 에러 발생.
**해결**: 근본 원인인 "중복 악기명이 애초에 저장 가능했던 것"을 막기 위해 `PostCreateRequestDTO`에 `@AssertTrue` 검증(`isInstrumentsUnique`)을 추가해 등록 시점에 400으로 차단. 동시에 `toMap`의 merge function을 `(a, b) -> a`로 지정해, 검증 도입 이전에 이미 유입된 레거시 중복 데이터에 대해서도 방어적으로 동작하도록 함(레거시 데이터 시나리오를 재현하는 회귀 테스트 별도 추가).

### 4. 콩쿠르 크롤러 페이지네이션 오작동 *(해당 도메인은 이후 KOPIS 공식 API로 대체됨 — 법적 리스크 회피)*
**증상**: 목록이 접수마감일 기준으로 정렬되어 있어 신규 항목이 뒤쪽 페이지에도 나타날 수 있는데, 기존 로직은 "한 페이지가 전부 중복이면 그 페이지에서 크롤링을 중단"하는 전제로 동작해 신규 항목을 누락할 가능성이 있었음. 또한 목록 URL의 경로 세그먼트를 페이지 번호로 오인해 실제로는 페이지 이동 없이 같은 카테고리를 반복 조회하고 있었음.
**해결**: 목록 페이지의 페이지네이션 링크(`pg=` 쿼리 파라미터)에서 총 페이지 수를 파싱해 전체 페이지를 순회하도록 변경하고, 페이지 단위 조기 종료 로직을 제거(개별 항목의 title+url 기준 중복 체크로 대체). 대상 사이트에 대한 예의상 요청 간 지연(300ms)을 추가하고, 총 페이지 파싱값이 비정상적으로 크게 나오는 경우를 대비한 상한(`HARD_CAP_PAGE`)을 별도로 둠.

### 5. CI가 처음부터 깨진 채로 계속 병합되던 문제
**증상**: `PostSpecificationTest`(`@DataJpaTest`) 4건이 CI에서 항상 실패.
**원인 분석**: `@DataJpaTest`의 기본 `ddl-auto`는 `create-drop`이지만, `application.properties`의 `spring.jpa.hibernate.ddl-auto=validate`가 이를 덮어쓰고 있었음(CI도 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`를 환경변수로 주입). 그 결과 테스트가 빈 H2 스키마를 `validate`하려다 `SchemaManagementException`으로 실패. 커밋 이력을 추적한 결과 이 테스트가 도입된 시점부터 이미 깨져 있었고, 이후 여러 PR이 **red 상태인 채로 계속 병합**되어 온 것을 확인.
**해결**: 해당 테스트 클래스에 `@TestPropertySource`로 `ddl-auto=create-drop`을 명시해 전역 설정을 오버라이드. 근본 원인(전역 설정과 테스트 슬라이스 기본값의 충돌)을 커밋 메시지에 남겨 재발 시 추적 가능하도록 기록.

### 6. Firebase 필터에서 던진 예외가 JSON이 아닌 컨테이너 기본 에러로 나가던 문제
**증상**: `Authorization: Bearer ` 헤더에서 토큰 부분이 빈 문자열이면 `verifyIdToken()`이 `IllegalArgumentException`을 던지는데, 이 예외가 `DispatcherServlet` **이전** 단계(Security 필터 체인)에서 발생해 `GlobalExceptionHandler`가 잡을 수 없고, 컨테이너 기본 에러 페이지(비-JSON, 500)로 응답이 나감.
**해결**: `FirebaseAuthFilter`에서 `idToken.isBlank()`를 사전에 체크해, 빈 토큰이면 검증 자체를 시도하지 않고 다음 필터로 넘겨 미인증 상태로 처리(인증이 필요한 경로는 이후 `AuthenticationEntryPoint`가 401 + 표준 응답 포맷으로 처리).

## 테스트

- 백엔드 27개 테스트 클래스: Service는 Mockito 기반 단위 테스트(DB·Firebase Mock으로 격리), Controller는 `@WebMvcTest` + `@Import(SecurityConfig.class)` + `MockMvc`로 인가 로직까지 검증.
- 인증이 필요한 컨트롤러 테스트는 `FirebaseAuthFilter`를 `@MockitoBean`으로 대체하고 `doAnswer`로 필터 체인을 그대로 통과시키는 패턴을 표준화해 재사용.
- GitHub Actions에서 PostgreSQL 16 서비스 컨테이너를 띄워 실제 운영 DB와 동일한 엔진으로 CI를 구성(Flyway가 스키마 생성 → Hibernate `validate`로 매핑 일치 검증).

## 향후 계획 (백로그)

- `ApplicationService`에 남아있는 `PostService → ApplicationService` 서비스 계층 의존 제거.
- Auth 회원가입 응답(`POST /api/auth/register`)을 명세(`createdAt`만 반환)에 맞게 경량화.
- 앱 이름을 "Obri"에서 "Poco a Poco"로 사용자 노출 영역 전체에 반영(현재 백엔드 로직·데이터는 전환 완료, 프론트 브랜드 표기만 남음).
- 소셜 로그인, 연습 피드백(LLM), 맞춤 모집글 추천, 매너 점수, 연주회 찜하기 기능.
