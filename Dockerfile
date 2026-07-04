# Backend image: builds the frontend, then the Spring Boot jar that serves it.
FROM node:22-slim AS frontend
WORKDIR /src
COPY frontend/package*.json ./
RUN npm ci || npm install
COPY frontend .
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /src
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /src/dist ./src/main/resources/static
RUN mvn -q -DskipTests package spotless:check

FROM eclipse-temurin:21-jre
WORKDIR /srv
COPY --from=backend /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
