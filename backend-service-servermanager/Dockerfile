FROM eclipse-temurin:21-jdk AS assembly
WORKDIR /source
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY backend-service-servermanager ./backend-service-servermanager
COPY backend-service/build.gradle.kts ./backend-service/build.gradle.kts
COPY common-dto/build.gradle.kts ./common-dto/build.gradle.kts
COPY paper-plugin/build.gradle.kts ./paper-plugin/build.gradle.kts
RUN chmod +x gradlew && ./gradlew :backend-service-servermanager:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=assembly /source/backend-service-servermanager/build/libs/caenis-manager.jar app.jar
ENV CAENIS_MANAGER_BIND=0.0.0.0
EXPOSE 8090
ENTRYPOINT ["java","-jar","app.jar"]
