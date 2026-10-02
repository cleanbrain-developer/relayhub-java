FROM node:26-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install
COPY frontend .
# npm run build (not a bare `npx vite build`): the build script is `tsc --noEmit && vite build`
# (see frontend/package.json) — a plain `vite build` only transpiles via esbuild and does not
# type-check, so a real type error would have silently produced a working Docker image before
# this fix (self-review finding, 2026-10-02: CI's own `test` job never ran anything
# frontend-related either — see the new ci.yml `frontend-test` job). `-- --outDir dist` forwards
# past the `&&` to vite build specifically: this stage's build context doesn't have frontend/ and
# src/ side by side the way the repo checkout does, so it can't write straight into
# ../src/main/resources/static (vite.config.ts's configured outDir for local dev) and instead
# builds into a local dist/ here, copied into the Java build stage explicitly below.
RUN npm run build -- --outDir dist

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
