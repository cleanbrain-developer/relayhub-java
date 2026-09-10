FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Dependency layer cached separately from source so a source-only change doesn't
# re-download the whole Gradle dependency graph.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S relayhub && adduser -S relayhub -G relayhub
COPY --from=build /app/build/libs/*.jar app.jar
USER relayhub

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
