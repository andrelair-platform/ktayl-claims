# syntax=docker/dockerfile:1
# ktayl-claims ACL — multi-stage build (Java 21 + Spring Boot). File named `Dockerfile` (repo convention).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
# Gatekeeper-compliant: run as a non-root user (runAsNonRoot).
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app
COPY --from=build /workspace/target/claims-acl-*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
