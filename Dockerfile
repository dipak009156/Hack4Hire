# Multi-stage Dockerfile for QUANTUM-LOCK Backend
# Stage 1: Build JAR using Maven & JDK 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Production JRE Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/quantum-lock-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms64m", "-Xmx350m", "-XX:+UseSerialGC", "-jar", "app.jar"]
