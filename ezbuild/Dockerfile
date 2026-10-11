# Multi-stage build: JDK 21 compiles, JRE 21 runs as a non-root user.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 app && mkdir -p /app/uploads && chown app /app/uploads
COPY --from=build /app/target/ezbuild-1.0.jar app.jar
USER app
ENV PORT=1010 SPRING_PROFILES_ACTIVE=prod
EXPOSE 1010
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
