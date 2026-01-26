FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /app

COPY pom.xml .

RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B || true

COPY src src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 5556
ENTRYPOINT ["java", "-jar", "app.jar"]