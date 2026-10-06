FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN sed -i 's/\r$//' mvnw && sh ./mvnw -B verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ninefold && adduser -S -G ninefold ninefold
WORKDIR /app
COPY --from=build /build/target/lore-fm-*.jar app.jar
USER ninefold
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
