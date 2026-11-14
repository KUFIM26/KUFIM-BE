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
# 습득물 사진이 쌓이는 경로. 여기에 named volume이 붙는데, Docker는 빈 볼륨을
# 이미지의 같은 경로에서 초기화하므로 소유자까지 따라갑니다. 이 줄이 없으면
# 볼륨이 root 소유로 만들어져 kufim 사용자가 사진을 저장하지 못합니다.
RUN mkdir -p /data/uploads && chown -R kufim:kufim /data
COPY --from=build /app/app.jar app.jar
USER kufim
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
