FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

COPY gradlew ./gradlew
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3   CMD wget -q -O /dev/null http://127.0.0.1:8080/api/agent/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
