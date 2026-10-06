# 서버를 컨테이너로 빌드/실행한다 (클라우드 VM에서 자바를 따로 설치하지 않기 위함). x86, Arm(Oracle Ampere) 모두 동작.
# 사용: docker-compose.prod.yml (docker compose -f docker-compose.prod.yml up -d --build)

# ── 1단계: 빌드 ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version
COPY src src
# 테스트는 빌드 속도를 위해 건너뛴다 (로컬/CI에서 따로 돌린다). plain jar가 아닌 실행 가능한 jar를 app.jar로 복사
RUN ./gradlew bootJar -x test --no-daemon --console=plain \
    && cp "$(ls build/libs/*.jar | grep -v plain | head -n 1)" /app.jar

# ── 2단계: 실행 ──
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
