# 백엔드 운영 이미지 — 빌드(JDK)와 실행(JRE)을 분리한 멀티스테이지.
# 시크릿(Firebase 키·DB 비밀번호·KOPIS 키)은 이미지에 넣지 않고 실행 시 주입한다(.dockerignore 참고).

# ── 1단계: 빌드 ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 레이어를 소스와 분리 — build.gradle이 안 바뀌면 의존성 다운로드를 캐시에서 재사용
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

# 테스트는 CI(backend.yml)가 담당하므로 이미지 빌드에서는 제외
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ── 2단계: 실행 ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# root가 아닌 전용 계정으로 실행
RUN useradd --system --no-create-home app
USER app

COPY --from=build /app/build/libs/*.jar app.jar

# 컨테이너 기본 시간대는 UTC — createdAt·공연 기간 비교 등 날짜 로직이 한국 시각 기준으로 돌도록 고정
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
