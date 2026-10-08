# Dockerfile for InmoNode-Backend
# Summary:
# Builds and runs the InmoNode-Backend application using Maven and Eclipse Temurin JDK 21.
# Description:
# Multi-stage build: the first stage compiles and packages the Spring Boot application,
# the second stage runs it on a lightweight JRE image. It sets the active Spring profile
# to 'prod' and exposes port 8080.

# Step 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy the sources and build the application
COPY src ./src
RUN mvn -B clean package -DskipTests

# Step 2: Create the runtime image
FROM eclipse-temurin:21-jre AS runtime
ENV SPRING_PROFILES_ACTIVE=prod
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Step 3: Configure and run the application
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# The 'prod' profile requires the following environment variables:
# - DATABASE_URL: The PostgreSQL host name or address.
# - DATABASE_PORT: The PostgreSQL port (default 5432).
# - DATABASE_NAME: The name of the database.
# - DATABASE_USER: The username for the database connection.
# - DATABASE_PASSWORD: The password for the database connection.
# - PORT: The port on which the application will run (default 8080).
# - JWT_SECRET: The secret used to sign JSON Web Tokens (at least 32 bytes).
# - JWT_EXPIRATION_SECONDS: The token lifetime in seconds (default 3600).
# - REFRESH_TOKEN_EXPIRATION_DAYS: The refresh token lifetime in days (default 30).
# - STAFF_{FIELD_AGENT,CATALOG_ADMIN,FINANCE_ADMIN}_EMAIL and ..._PASSWORD_HASH: The initial staff
#   accounts (BCrypt hashes), inserted by the first startup on an empty database.
