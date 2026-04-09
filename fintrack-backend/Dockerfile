# ---- Build stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends tesseract-ocr poppler-utils \
  && rm -rf /var/lib/apt/lists/*

RUN groupadd -r fintrack && useradd -r -g fintrack fintrack
USER fintrack

COPY --from=builder /app/target/fintrack-backend-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
