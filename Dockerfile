# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom and install dependencies first (caches layers)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# IMPORTANT: Copy the tools directory into the container
COPY tools ./tools

# Expose port 8080
EXPOSE 8080

# --- CHANGE IS HERE ---
# Added: -Xmx384m (Limit heap to 384MB, leaving RAM for OS overhead)
# Added: -XX:+UseSerialGC (Single-threaded GC, essential for 0.1 CPU)
ENTRYPOINT ["java", "-Xmx384m", "-XX:+UseSerialGC", "-jar", "app.jar"]