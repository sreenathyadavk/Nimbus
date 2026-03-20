FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace/app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy source
COPY src src

# Build application
RUN ./gradlew build -x test

# Runtime
FROM eclipse-temurin:17-jre-jammy
VOLUME /tmp
WORKDIR /app

# Ensure storage directory exists
RUN mkdir -p /app/storage

COPY --from=build /workspace/app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
