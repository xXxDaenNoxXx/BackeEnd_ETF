# --- Etapa 1: compilacion (Maven + JDK 17) ---
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# --- Etapa 2: imagen liviana de ejecucion (JRE, usuario sin privilegios) ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /data/output && chown -R spring:spring /data
COPY --from=build /app/target/*.jar app.jar
USER spring
ENV BATCH_OUTPUT_DIR=/data/output
EXPOSE 8104
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
