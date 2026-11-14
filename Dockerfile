FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 navesitas && useradd --uid 10001 --gid navesitas --no-create-home navesitas \
    && mkdir -p /var/data/almacen && chown -R navesitas:navesitas /app /var/data
COPY --from=build --chown=navesitas:navesitas /build/target/registro-naves-0.0.1-SNAPSHOT.jar /app/app.jar
USER navesitas
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
