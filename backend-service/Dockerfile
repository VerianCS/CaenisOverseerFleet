FROM eclipse-temurin:21-jdk AS assembly
WORKDIR /source
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY common-dto ./common-dto
COPY backend-service ./backend-service
COPY backend-service-servermanager/build.gradle.kts ./backend-service-servermanager/build.gradle.kts
COPY paper-plugin/build.gradle.kts ./paper-plugin/build.gradle.kts
RUN chmod +x gradlew && ./gradlew :backend-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=assembly /source/backend-service/build/libs/caenis-core.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
