# Use OpenJDK 11 as base image
FROM openjdk:11-jre-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the compiled JAR file into the container
COPY target/shipProxy-1.0.0-jar-with-dependencies.jar /app/shipProxy.jar

# Expose the port for incoming requests
EXPOSE 8080

# Command to run the JAR file
CMD ["java", "-jar", "shipProxy.jar"]
