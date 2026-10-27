# syntax=docker/dockerfile:1.7

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests run in CI (./mvnw verify); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true package \
 && java -Djarmode=tools -jar target/agentledger.jar extract --layers --launcher --destination target/extracted

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/application/ ./
USER app:app
ENV SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
