# KUFIM-BE 운영 이미지. deploy/docker-compose.yml에서 빌드합니다. (amd64·arm64 모두 지원)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home kufim
COPY --from=build /app/app.jar app.jar
USER kufim
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
