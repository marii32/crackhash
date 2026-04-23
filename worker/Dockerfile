FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle build

FROM eclipse-temurin:21-jdk

WORKDIR /app


COPY requests/build/libs/requests-1.0-SNAPSHOT-plain.jar requests.jar


COPY worker/build/libs/worker-0.0.1-SNAPSHOT.jar worker.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "worker.jar"]