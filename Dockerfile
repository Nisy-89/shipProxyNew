# Use OpenJDK 11 as base image
FROM openjdk:11-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the compiled JAR file into the container
COPY target/shipProxy-0.0.1-SNAPSHOT.jar app.jar

# Expose the port for incoming requests
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
