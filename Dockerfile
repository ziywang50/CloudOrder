FROM gradle:8.10-jdk21 AS build
ARG SERVICE
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

RUN set -e; \
    ./gradlew :${SERVICE}:bootJar --no-daemon; \
    jar=$(ls /workspace/${SERVICE}/build/libs/*.jar | grep -v 'plain.jar' | head -n 1); \
    cp "$jar" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG SERVICE

RUN apk add --no-cache curl

COPY --from=build /workspace/app.jar /app/app.jar
COPY config/rsa.pub /app/config/rsa.pub

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]