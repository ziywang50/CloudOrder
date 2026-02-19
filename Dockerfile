ARG SERVICE
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY common common
COPY apiGateway apiGateway
COPY cartService cartService
COPY customerService customerService
COPY eurekaServer eurekaServer
COPY orderCommandService orderCommandService
COPY OrderQueryService OrderQueryService
COPY productService productService
COPY secKillService secKillService

RUN ./gradlew :${SERVICE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/${SERVICE}/build/libs/*.jar /app/app.jar
COPY config/rsa.pub /app/config/rsa.pub

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
