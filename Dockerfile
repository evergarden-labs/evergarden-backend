# 빌드 스테이지 — Gradle wrapper로 부트 JAR를 만든다.
# 의존성 관련 파일을 먼저 복사해 그 레이어만 캐싱되게 한다 — src/만 바뀌었을 때
# 매번 의존성을 새로 받지 않는다.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

COPY src/ src/
# 테스트는 Testcontainers로 도커 소켓이 필요해 이미지 빌드 안에서 못 돌린다 —
# CI가 이미 별도로 ./gradlew test를 돌리므로 여기선 건너뛴다.
RUN ./gradlew bootJar --no-daemon -x test

# 실행 스테이지 — JDK가 아니라 JRE만 담아 이미지를 가볍게 한다.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 컨테이너가 뚫려도 루트 권한을 안 주기 위해 전용 유저로 실행한다.
RUN groupadd -r evergarden && useradd -r -g evergarden evergarden
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
RUN chown evergarden:evergarden app.jar
USER evergarden

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
