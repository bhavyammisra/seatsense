# One image for the hosted demo (B3.2): the React app is built first and served by the Spring Boot backend,
# so the pages, the API and the /ws socket share one address.

FROM node:24-alpine AS web
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# The Maven image already has Maven, so the build does not depend on the wrapper's download.
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /app/backend
COPY backend/ ./
COPY --from=web /app/frontend/dist/ src/main/resources/static/
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /app/backend/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=cloud
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
