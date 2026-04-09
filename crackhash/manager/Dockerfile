
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle build
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Копируем JAR-файл common
COPY requests/build/libs/requests-1.0-SNAPSHOT-plain.jar requests.jar

# Копируем JAR-файл manager-service
COPY manager/build/libs/manager-0.0.1-SNAPSHOT.jar manager.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "manager.jar"]