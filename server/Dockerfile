# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY btry-platform/pom.xml btry-platform/
COPY btry-contracts/pom.xml btry-contracts/
COPY btry-app/pom.xml btry-app/
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY btry-platform/src ./btry-platform/src
COPY btry-contracts/src ./btry-contracts/src
COPY btry-app/src ./btry-app/src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jdk-alpine
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Duser.timezone=Asia/Shanghai -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20M"
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app && mkdir -p /app/logs && chown -R app:app /app
COPY --chown=app:app --from=build /workspace/btry-app/target/btry-app-*-exec.jar /app/app.jar
USER app
EXPOSE 8084 9091
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
