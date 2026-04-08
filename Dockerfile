# 构建：先在宿主机执行 mvn -DskipTests package
# docker build -t vagent:local .
# docker run --rm -p 8080:8080 -e SPRING_DATASOURCE_URL=... vagent:local
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ARG JAR_FILE=target/vagent-0.1.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
