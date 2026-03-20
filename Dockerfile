# ── Stage 1: Build ──────────────────────────────────────────────────────────
# Uses the official Maven image so the host machine does not need Maven installed.
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /build

# Copy POMs first so Docker can cache the dependency-download layer separately
# from the source code layer — speeds up rebuilds when only source changes.
COPY pom.xml .
COPY common/pom.xml        common/pom.xml
COPY database-node/pom.xml database-node/pom.xml
COPY load-tester/pom.xml   load-tester/pom.xml

RUN mvn dependency:go-offline -pl common,database-node -am -q

COPY common/src        common/src
COPY database-node/src database-node/src

RUN mvn clean package -pl common,database-node -am -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Minimal JRE image — the full JDK is not needed at runtime.
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /build/database-node/target/database-node-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

# All node configuration (MODE, ROLE, FOLLOWER_URLS, etc.) is injected at
# runtime via environment variables — see docker-compose files or deploy.sh.
ENTRYPOINT ["java", "-jar", "app.jar"]
