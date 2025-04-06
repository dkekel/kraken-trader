FROM ubuntu:latest
LABEL authors="dkeke"

# Use Java 21 base image for the build process
FROM eclipse-temurin:21-jdk AS build

LABEL description="Build step using Java 21 for KrakenTraderApplication"

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and project files
COPY gradlew /app/gradlew
COPY gradle /app/gradle
COPY build.gradle /app/build.gradle
COPY settings.gradle /app/settings.gradle
COPY src /app/src

# Grant execute permissions to Gradle wrapper
RUN chmod +x ./gradlew

# Build the application using Gradle
RUN ./gradlew build --no-daemon

# Final runtime image
FROM ubuntu:latest

LABEL description="Runtime image for KrakenTraderApplication"

# Install Java 21 JRE
RUN apt-get update && apt-get install -y openjdk-21-jre-headless && apt-get clean

# Set working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/build/libs/KrakenTraderApplication.jar /app/KrakenTraderApplication.jar

# Set the entry point
ENTRYPOINT ["java", "-jar", "/app/KrakenTraderApplication.jar"]