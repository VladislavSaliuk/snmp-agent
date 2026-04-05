FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/snmp-agent-1.0-SNAPSHOT.jar app.jar

EXPOSE 161/udp

ENTRYPOINT ["java", "-jar", "app.jar"]