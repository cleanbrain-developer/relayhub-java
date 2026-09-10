FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install
COPY frontend .
# Writes straight into ../src/main/resources/static (see frontend/vite.config.ts) — but that
# path doesn't exist yet in this stage (frontend/ was copied standalone, not alongside src/), so
# build into a local dist/ here instead and copy it into the Java build stage explicitly below.
RUN npx vite build --outDir dist

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Dependency layer cached separately from source so a source-only change doesn't
# re-download the whole Gradle dependency graph.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# The built SPA becomes part of Spring Boot's static resources — same place `vite build` writes
# to in local dev (frontend/vite.config.ts's build.outDir), just assembled explicitly here since
# this stage's build context doesn't have the frontend/ and src/ directories side by side the
# way the repo checkout does.
COPY --from=frontend-build /frontend/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S relayhub && adduser -S relayhub -G relayhub
COPY --from=build /app/build/libs/*.jar app.jar
USER relayhub

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
